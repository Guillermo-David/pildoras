package io.github.guillermo_david.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public final class TursoMigrationService {

    public static final class Result {
        public final int pildoras;
        public final int tags;
        public final int pildoraTag;
        public final int drafts;
        public final int pildoraLink;

        Result(int pildoras, int tags, int pildoraTag, int drafts, int pildoraLink) {
            this.pildoras = pildoras;
            this.tags = tags;
            this.pildoraTag = pildoraTag;
            this.drafts = drafts;
            this.pildoraLink = pildoraLink;
        }

        public int total() { return pildoras + tags + pildoraTag + drafts + pildoraLink; }
    }

    public static Result migrateLocalToTurso() throws Exception {
        if (!TursoConfig.isConfigured()) {
            throw new IllegalStateException("Turso no esta configurado.");
        }

        Path localDb = getLocalDbPath();
        System.out.println("[Migration] Local DB path: " + localDb);
        System.out.println("[Migration] Local DB exists: " + Files.exists(localDb));
        if (!Files.exists(localDb)) {
            throw new IllegalStateException("No se encontro la base de datos local en: " + localDb);
        }

        String localUrl = "jdbc:sqlite:" + localDb;
        String tursoUrl = TursoConfig.toJdbcUrl(TursoConfig.getUrl());
        System.out.println("[Migration] Turso JDBC URL: " + tursoUrl);

        String tursoToken = TursoConfig.getToken();

        System.out.println("[Migration] Opening local connection...");
        try (Connection local = DriverManager.getConnection(localUrl)) {
            System.out.println("[Migration] Local connection OK");

            System.out.println("[Migration] Opening Turso connection...");
            try (Connection turso = DriverManager.getConnection(tursoUrl, null, tursoToken)) {
                System.out.println("[Migration] Turso connection OK");

                try (Statement s = local.createStatement()) {
                    s.execute("PRAGMA foreign_keys=OFF");
                }

                System.out.println("[Migration] Migrating pildoras...");
                int nPildoras = migratePildoras(local, turso);
                System.out.println("[Migration] pildoras: " + nPildoras);

                System.out.println("[Migration] Migrating tags...");
                int nTags = migrateTags(local, turso);
                System.out.println("[Migration] tags: " + nTags);

                System.out.println("[Migration] Migrating pildora_tag...");
                int nPildoraTag = migratePildoraTag(local, turso);
                System.out.println("[Migration] pildora_tag: " + nPildoraTag);

                System.out.println("[Migration] Migrating drafts...");
                int nDrafts = migrateDrafts(local, turso);
                System.out.println("[Migration] drafts: " + nDrafts);

                System.out.println("[Migration] Migrating pildora_link...");
                int nPildoraLink = migratePildoraLink(local, turso);
                System.out.println("[Migration] pildora_link: " + nPildoraLink);

                System.out.println("[Migration] Done!");
                return new Result(nPildoras, nTags, nPildoraTag, nDrafts, nPildoraLink);
            }
        }
    }

    private static int migratePildoras(Connection local, Connection turso) throws SQLException {
        int count = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned, descripcion_cipher, descripcion_iv, protegida FROM pildoras")) {

            String sql = "INSERT OR REPLACE INTO pildoras (id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned, descripcion_cipher, descripcion_iv, protegida) VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = turso.prepareStatement(sql)) {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt("id"));
                    ps.setString(2, rs.getString("titulo"));
                    setStringOrNull(ps, 3, rs.getString("descripcion"));
                    setStringOrNull(ps, 4, rs.getString("fecha_creacion"));
                    setStringOrNull(ps, 5, rs.getString("fecha_actualizacion"));
                    ps.setInt(6, rs.getInt("favorita"));
                    ps.setInt(7, rs.getInt("pinned"));
                    setBytesOrNull(ps, 8, rs.getBytes("descripcion_cipher"));
                    setBytesOrNull(ps, 9, rs.getBytes("descripcion_iv"));
                    ps.setInt(10, rs.getInt("protegida"));
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    private static int migrateTags(Connection local, Connection turso) throws SQLException {
        int count = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nombre FROM tags")) {

            String sql = "INSERT OR REPLACE INTO tags (id, nombre) VALUES (?,?)";
            try (PreparedStatement ps = turso.prepareStatement(sql)) {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt("id"));
                    ps.setString(2, rs.getString("nombre"));
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    private static int migratePildoraTag(Connection local, Connection turso) throws SQLException {
        int count = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT pildora_id, tag_id FROM pildora_tag")) {

            String sql = "INSERT OR REPLACE INTO pildora_tag (pildora_id, tag_id) VALUES (?,?)";
            try (PreparedStatement ps = turso.prepareStatement(sql)) {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt("pildora_id"));
                    ps.setInt(2, rs.getInt("tag_id"));
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    private static int migrateDrafts(Connection local, Connection turso) throws SQLException {
        int count = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, titulo, contenido, contenido_cipher, contenido_iv, protegida, fecha_creacion, fecha_actualizacion FROM drafts")) {

            String sql = "INSERT OR REPLACE INTO drafts (id, titulo, contenido, contenido_cipher, contenido_iv, protegida, fecha_creacion, fecha_actualizacion) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = turso.prepareStatement(sql)) {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt("id"));
                    setStringOrNull(ps, 2, rs.getString("titulo"));
                    setStringOrNull(ps, 3, rs.getString("contenido"));
                    setBytesOrNull(ps, 4, rs.getBytes("contenido_cipher"));
                    setBytesOrNull(ps, 5, rs.getBytes("contenido_iv"));
                    ps.setInt(6, rs.getInt("protegida"));
                    setStringOrNull(ps, 7, rs.getString("fecha_creacion"));
                    setStringOrNull(ps, 8, rs.getString("fecha_actualizacion"));
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    private static int migratePildoraLink(Connection local, Connection turso) throws SQLException {
        int count = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT from_id, to_id, kind, position, created_at FROM pildora_link")) {

            String sql = "INSERT OR REPLACE INTO pildora_link (from_id, to_id, kind, position, created_at) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = turso.prepareStatement(sql)) {
                while (rs.next()) {
                    ps.setInt(1, rs.getInt("from_id"));
                    ps.setInt(2, rs.getInt("to_id"));
                    ps.setString(3, rs.getString("kind"));
                    int pos = rs.getInt("position");
                    if (rs.wasNull()) ps.setObject(4, null);
                    else ps.setInt(4, pos);
                    setStringOrNull(ps, 5, rs.getString("created_at"));
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    private static Path getLocalDbPath() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Path.of(appdata).resolve("Pildoras").resolve("knowledgebase.db");
    }

    private static void setStringOrNull(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null) ps.setObject(idx, null);
        else ps.setString(idx, val);
    }

    private static void setBytesOrNull(PreparedStatement ps, int idx, byte[] val) throws SQLException {
        if (val == null) ps.setObject(idx, null);
        else ps.setObject(idx, val);
    }
}
