package io.github.guillermo_david.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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

            try (Statement ts = turso.createStatement()) {
                while (rs.next()) {
                    String sql = "INSERT OR REPLACE INTO pildoras (id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned, descripcion_cipher, descripcion_iv, protegida) VALUES ("
                        + rs.getInt("id") + ", "
                        + esc(rs.getString("titulo")) + ", "
                        + esc(rs.getString("descripcion")) + ", "
                        + esc(rs.getString("fecha_creacion")) + ", "
                        + esc(rs.getString("fecha_actualizacion")) + ", "
                        + rs.getInt("favorita") + ", "
                        + rs.getInt("pinned") + ", "
                        + escBlob(rs.getBytes("descripcion_cipher")) + ", "
                        + escBlob(rs.getBytes("descripcion_iv")) + ", "
                        + rs.getInt("protegida") + ")";
                    ts.execute(sql);
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

            try (Statement ts = turso.createStatement()) {
                while (rs.next()) {
                    String sql = "INSERT OR REPLACE INTO tags (id, nombre) VALUES ("
                        + rs.getInt("id") + ", "
                        + esc(rs.getString("nombre")) + ")";
                    ts.execute(sql);
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

            try (Statement ts = turso.createStatement()) {
                while (rs.next()) {
                    String sql = "INSERT OR REPLACE INTO pildora_tag (pildora_id, tag_id) VALUES ("
                        + rs.getInt("pildora_id") + ", "
                        + rs.getInt("tag_id") + ")";
                    ts.execute(sql);
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

            try (Statement ts = turso.createStatement()) {
                while (rs.next()) {
                    String sql = "INSERT OR REPLACE INTO drafts (id, titulo, contenido, contenido_cipher, contenido_iv, protegida, fecha_creacion, fecha_actualizacion) VALUES ("
                        + rs.getInt("id") + ", "
                        + esc(rs.getString("titulo")) + ", "
                        + esc(rs.getString("contenido")) + ", "
                        + escBlob(rs.getBytes("contenido_cipher")) + ", "
                        + escBlob(rs.getBytes("contenido_iv")) + ", "
                        + rs.getInt("protegida") + ", "
                        + esc(rs.getString("fecha_creacion")) + ", "
                        + esc(rs.getString("fecha_actualizacion")) + ")";
                    ts.execute(sql);
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

            try (Statement ts = turso.createStatement()) {
                while (rs.next()) {
                    int pos = rs.getInt("position");
                    String posVal = rs.wasNull() ? "NULL" : String.valueOf(pos);
                    String sql = "INSERT OR REPLACE INTO pildora_link (from_id, to_id, kind, position, created_at) VALUES ("
                        + rs.getInt("from_id") + ", "
                        + rs.getInt("to_id") + ", "
                        + esc(rs.getString("kind")) + ", "
                        + posVal + ", "
                        + esc(rs.getString("created_at")) + ")";
                    ts.execute(sql);
                    count++;
                }
            }
        }
        return count;
    }

    private static String esc(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("'", "''") + "'";
    }

    private static String escBlob(byte[] val) {
        if (val == null) return "NULL";
        StringBuilder sb = new StringBuilder(val.length * 2 + 3);
        sb.append("X'");
        for (byte b : val) {
            sb.append(String.format("%02x", b & 0xff));
        }
        sb.append("'");
        return sb.toString();
    }

    private static Path getLocalDbPath() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Path.of(appdata).resolve("Pildoras").resolve("knowledgebase.db");
    }
}
