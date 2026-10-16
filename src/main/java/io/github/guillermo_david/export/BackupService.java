package io.github.guillermo_david.export;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.guillermo_david.dao.SqlHelper;
import io.github.guillermo_david.db.DatabaseHelper;

public class BackupService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public record BackupData(
        String version,
        String createdAt,
        List<PildoraRow> pildoras,
        List<TagRow> tags,
        List<PildoraTagRow> pildoraTags,
        List<DraftRow> drafts,
        List<PildoraLinkRow> pildoraLinks
    ) {}

    public record PildoraRow(int id, String titulo, String descripcion,
            String fechaCreacion, String fechaActualizacion,
            boolean favorita, boolean pinned, boolean protegida,
            String cipherBase64, String ivBase64, boolean oculta) {}

    public record TagRow(int id, String nombre) {}
    public record PildoraTagRow(int pildoraId, int tagId) {}
    public record DraftRow(int id, String titulo, String contenido,
            String cipherBase64, String ivBase64, boolean protegida,
            String fechaCreacion, String fechaActualizacion) {}
    public record PildoraLinkRow(int fromId, int toId, String kind) {}

    public static void exportToFile(File file) throws Exception {
        var conn = DatabaseHelper.getInstance().getConnection();

        List<PildoraRow> pildoras = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id,titulo,descripcion,fecha_creacion,fecha_actualizacion,"
                 + "favorita,pinned,protegida,hex(descripcion_cipher) AS ch,hex(descripcion_iv) AS iv,oculta "
                 + "FROM pildoras ORDER BY id")) {
            while (rs.next()) {
                pildoras.add(new PildoraRow(
                    rs.getInt("id"), rs.getString("titulo"), rs.getString("descripcion"),
                    rs.getString("fecha_creacion"), rs.getString("fecha_actualizacion"),
                    rs.getInt("favorita") == 1, rs.getInt("pinned") == 1,
                    rs.getInt("protegida") == 1,
                    hexToBase64(rs.getString("ch")), hexToBase64(rs.getString("iv")),
                    rs.getInt("oculta") == 1
                ));
            }
        }

        List<TagRow> tags = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,nombre FROM tags ORDER BY id")) {
            while (rs.next()) {
                tags.add(new TagRow(rs.getInt("id"), rs.getString("nombre")));
            }
        }

        List<PildoraTagRow> pildoraTags = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT pildora_id,tag_id FROM pildora_tag ORDER BY pildora_id,tag_id")) {
            while (rs.next()) {
                pildoraTags.add(new PildoraTagRow(rs.getInt("pildora_id"), rs.getInt("tag_id")));
            }
        }

        List<DraftRow> drafts = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id,titulo,contenido,hex(contenido_cipher) AS ch,hex(contenido_iv) AS iv,"
                 + "protegida,fecha_creacion,fecha_actualizacion FROM drafts ORDER BY id")) {
            while (rs.next()) {
                drafts.add(new DraftRow(
                    rs.getInt("id"), rs.getString("titulo"), rs.getString("contenido"),
                    hexToBase64(rs.getString("ch")), hexToBase64(rs.getString("iv")),
                    rs.getInt("protegida") == 1,
                    rs.getString("fecha_creacion"), rs.getString("fecha_actualizacion")
                ));
            }
        }

        List<PildoraLinkRow> links = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT from_id,to_id,kind FROM pildora_link ORDER BY from_id,to_id")) {
            while (rs.next()) {
                links.add(new PildoraLinkRow(rs.getInt("from_id"), rs.getInt("to_id"), rs.getString("kind")));
            }
        }

        BackupData backup = new BackupData(
            "1.0", LocalDateTime.now().format(FMT),
            pildoras, tags, pildoraTags, drafts, links
        );

        try (FileWriter fw = new FileWriter(file)) {
            GSON.toJson(backup, fw);
        }
    }

    public static BackupData readBackup(File file) throws Exception {
        try (FileReader fr = new FileReader(file)) {
            return GSON.fromJson(fr, BackupData.class);
        }
    }

    public static int[] importFromFile(File file) throws Exception {
        BackupData data = readBackup(file);
        var conn = DatabaseHelper.getInstance().getConnection();
        boolean turso = DatabaseHelper.getInstance().isUsingTurso();

        int pCount = 0, tCount = 0, dCount = 0;

        if (!turso) {
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM pildora_link");
                st.execute("DELETE FROM pildora_tag");
                st.execute("DELETE FROM drafts");
                st.execute("DELETE FROM pildoras");
                st.execute("DELETE FROM tags");
            }
        } else {
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM pildora_link");
                st.execute("DELETE FROM pildora_tag");
                st.execute("DELETE FROM drafts");
                st.execute("DELETE FROM pildoras");
                st.execute("DELETE FROM tags");
            }
        }

        for (TagRow t : data.tags) {
            String sql = "INSERT INTO tags (id,nombre) VALUES ("
                    + t.id + "," + esc(t.nombre) + ")";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
            tCount++;
        }

        for (PildoraRow p : data.pildoras) {
            String cipher = base64ToEscBlob(p.cipherBase64);
            String iv = base64ToEscBlob(p.ivBase64);
            String sql = "INSERT INTO pildoras (id,titulo,descripcion,fecha_creacion,fecha_actualizacion,"
                    + "favorita,pinned,protegida,descripcion_cipher,descripcion_iv,oculta) VALUES ("
                    + p.id + "," + esc(p.titulo) + "," + esc(p.descripcion) + ","
                    + esc(p.fechaCreacion) + "," + esc(p.fechaActualizacion) + ","
                    + (p.favorita ? 1 : 0) + "," + (p.pinned ? 1 : 0) + ","
                    + (p.protegida ? 1 : 0) + "," + cipher + "," + iv + ","
                    + (p.oculta ? 1 : 0) + ")";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
            pCount++;
        }

        for (PildoraTagRow pt : data.pildoraTags) {
            String sql = "INSERT OR IGNORE INTO pildora_tag (pildora_id,tag_id) VALUES ("
                    + pt.pildoraId + "," + pt.tagId + ")";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
        }

        for (DraftRow d : data.drafts) {
            String cipher = base64ToEscBlob(d.cipherBase64);
            String iv = base64ToEscBlob(d.ivBase64);
            String sql = "INSERT INTO drafts (id,titulo,contenido,contenido_cipher,contenido_iv,"
                    + "protegida,fecha_creacion,fecha_actualizacion) VALUES ("
                    + d.id + "," + esc(d.titulo) + "," + esc(d.contenido) + ","
                    + cipher + "," + iv + "," + (d.protegida ? 1 : 0) + ","
                    + esc(d.fechaCreacion) + "," + esc(d.fechaActualizacion) + ")";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
            dCount++;
        }

        for (PildoraLinkRow l : data.pildoraLinks) {
            String sql = "INSERT OR IGNORE INTO pildora_link (from_id,to_id,kind) VALUES ("
                    + l.fromId + "," + l.toId + "," + esc(l.kind) + ")";
            try (Statement st = conn.createStatement()) { st.execute(sql); }
        }

        return new int[]{pCount, tCount, dCount};
    }

    private static String esc(String val) {
        if (val == null || val.isEmpty()) return "NULL";
        return SqlHelper.esc(val);
    }

    private static String hexToBase64(String hex) {
        byte[] bytes = SqlHelper.hexToBytes(hex);
        if (bytes == null) return null;
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String base64ToEscBlob(String b64) {
        if (b64 == null) return "NULL";
        return SqlHelper.escBlob(Base64.getDecoder().decode(b64));
    }
}
