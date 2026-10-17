package io.github.guillermo_david.export;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    private static final String ENC_FORMAT = "pildoras-enc-1";
    private static final int PBKDF2_ITER = 200_000;

    public static class WrongPasswordException extends Exception {
        public WrongPasswordException() { super("Contraseña incorrecta o fichero dañado"); }
    }

    public static boolean isEncrypted(File file) {
        try {
            var el = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return el.isJsonObject() && ENC_FORMAT.equals(
                    el.getAsJsonObject().has("format") ? el.getAsJsonObject().get("format").getAsString() : null);
        } catch (Exception e) {
            return false;
        }
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(password, salt, PBKDF2_ITER, 256);
        try {
            return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
                    .getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    static String encrypt(String json, char[] password) throws Exception {
        var rnd = new SecureRandom();
        byte[] salt = new byte[16], iv = new byte[12];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        var c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(128, iv));
        byte[] data = c.doFinal(json.getBytes(StandardCharsets.UTF_8));
        var o = new JsonObject();
        o.addProperty("format", ENC_FORMAT);
        o.addProperty("salt", Base64.getEncoder().encodeToString(salt));
        o.addProperty("iv", Base64.getEncoder().encodeToString(iv));
        o.addProperty("data", Base64.getEncoder().encodeToString(data));
        return GSON.toJson(o);
    }

    static String decrypt(String envelope, char[] password) throws Exception {
        var o = JsonParser.parseString(envelope).getAsJsonObject();
        byte[] salt = Base64.getDecoder().decode(o.get("salt").getAsString());
        byte[] iv = Base64.getDecoder().decode(o.get("iv").getAsString());
        byte[] data = Base64.getDecoder().decode(o.get("data").getAsString());
        try {
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(data), StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new WrongPasswordException();
        }
    }

    public static void exportToFile(File file) throws Exception {
        exportToFile(file, null);
    }

    public static void exportToFile(File file, char[] password) throws Exception {
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

        String json = GSON.toJson(backup);
        if (password != null && password.length > 0)
            json = encrypt(json, password);
        Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
    }

    public static BackupData readBackup(File file, char[] password) throws Exception {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (isEncrypted(file)) {
            if (password == null || password.length == 0)
                throw new WrongPasswordException();
            text = decrypt(text, password);
        }
        return GSON.fromJson(text, BackupData.class);
    }

    public static int[] importFromFile(File file) throws Exception {
        return importFromFile(file, null);
    }

    public static int[] importFromFile(File file, char[] password) throws Exception {
        BackupData data = readBackup(file, password);
        var conn = DatabaseHelper.getInstance().getConnection();

        int pCount = 0, tCount = 0, dCount = 0;

        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM pildora_link");
            st.execute("DELETE FROM pildora_tag");
            st.execute("DELETE FROM drafts");
            st.execute("DELETE FROM pildoras");
            st.execute("DELETE FROM tags");
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
