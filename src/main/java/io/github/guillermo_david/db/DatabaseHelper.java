package io.github.guillermo_david.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseHelper {

	private static final String APP_FOLDER = "Pildoras";
    private static final String DB_NAME = "knowledgebase.db";

    private static DatabaseHelper instance;
    private Connection connection;
    private String jdbcUrl; // <- para poder reabrir

    private DatabaseHelper() {
        try {
            Path target = getDbPath();
            migrateOldLocationIfNeeded(target);
            Files.createDirectories(target.getParent());

            this.jdbcUrl = "jdbc:sqlite:" + target.toString();
            reopenIfNeeded();           // 👈 abre conexión
            applyPragmas(connection);   // 👈 PRAGMAs siempre tras abrir

            initializeDatabase();

            // DEBUG útil
//            System.out.println("[DB] APPDATA=" + System.getenv("APPDATA"));
//            System.out.println("[DB] LOCALAPPDATA=" + System.getenv("LOCALAPPDATA"));
//            System.out.println("[DB] user.dir=" + System.getProperty("user.dir"));
//            System.out.println("[DB] DB_PATH=" + DatabaseHelper.getDatabasePathForDebug());
//            System.out.println("[DB] JDBC URL=" + jdbcUrl);

        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error inicializando la base de datos", e);
        }
    }

    public static synchronized DatabaseHelper getInstance() {
        if (instance == null) instance = new DatabaseHelper();
        return instance;
    }

    /** Devuelve SIEMPRE una conexión abierta. Reabre si estaba cerrada. */
    public synchronized Connection getConnection() {
        try {
            reopenIfNeeded();
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo obtener la conexión SQLite", e);
        }
    }

    /** Cierra de forma explícita (normalmente no necesitas llamarlo). */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignore) {}
    }

    // --- Internos ---

    /** Reabre si no hay conexión o está cerrada. */
    private synchronized void reopenIfNeeded() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(true);
        }
    }

    /** Aplica PRAGMAs recomendables. Llamar SIEMPRE tras (re)abrir. */
    private static void applyPragmas(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            throw new RuntimeException("No se pudieron aplicar PRAGMAs", e);
        }
    }

    // --- Paths ---

    private static Path getRoamingDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Paths.get(appdata).toAbsolutePath().normalize();
    }

    private static Path getLocalDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Paths.get(local);
        Path home = Paths.get(System.getProperty("user.home"));
        return home.resolve("AppData").resolve("Local");
    }

    private static Path getDbPath() {
        return getRoamingDir().resolve(APP_FOLDER).resolve(DB_NAME).toAbsolutePath().normalize();
    }

    public static Path getDatabasePathForDebug() {
        return getDbPath();
    }

    private static void migrateOldLocationIfNeeded(Path dstDb) throws IOException {
        Path dstDir = dstDb.getParent();
        Files.createDirectories(dstDir);

        if (Files.exists(dstDb) && Files.size(dstDb) > 0) return;

        Path srcDb = getLocalDir().resolve(APP_FOLDER).resolve(DB_NAME);
        if (!Files.exists(srcDb)) return;

        boolean dstExists = Files.exists(dstDb);
        if (dstExists && Files.size(dstDb) == 0) {
            // opcional: backup del vacío
        }

        try {
            Files.move(srcDb, dstDb, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(srcDb, dstDb, StandardCopyOption.REPLACE_EXISTING);
        }

        moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-wal"), dstDb.resolveSibling(DB_NAME + "-wal"));
        moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-shm"), dstDb.resolveSibling(DB_NAME + "-shm"));
    }

    private static void moveSiblingIfPresent(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) return;
        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // --- Esquema + migraciones idempotentes ---

    private void initializeDatabase() throws SQLException {
        // Asegura conexión abierta por si alguien la cerró antes de entrar aquí
        reopenIfNeeded();
        applyPragmas(connection);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pildoras (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo TEXT NOT NULL,
                    descripcion TEXT,
                    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    fecha_actualizacion TIMESTAMP,
                    favorita INTEGER NOT NULL DEFAULT 0,
                    pinned   INTEGER NOT NULL DEFAULT 0,
                    descripcion_cipher BLOB,
                    descripcion_iv     BLOB,
                    protegida INTEGER NOT NULL DEFAULT 0
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT UNIQUE NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pildora_tag (
                    pildora_id INTEGER NOT NULL,
                    tag_id     INTEGER NOT NULL,
                    PRIMARY KEY (pildora_id, tag_id),
                    FOREIGN KEY (pildora_id) REFERENCES pildoras(id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id)     REFERENCES tags(id)     ON DELETE RESTRICT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS drafts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo TEXT,
                    contenido TEXT,
                    contenido_cipher BLOB,
                    contenido_iv BLOB,
                    protegida INTEGER NOT NULL DEFAULT 0,
                    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    fecha_actualizacion TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pildora_link (
                  from_id    INTEGER NOT NULL,
                  to_id      INTEGER NOT NULL,
                  kind       TEXT    NOT NULL DEFAULT 'ref',
                  position   INTEGER NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE (from_id, to_id, kind),
                  FOREIGN KEY (from_id) REFERENCES pildoras(id) ON DELETE CASCADE,
                  FOREIGN KEY (to_id)   REFERENCES pildoras(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_favorita ON pildoras(favorita)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_pinned   ON pildoras(pinned)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_protegida ON pildoras(protegida)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_drafts_fecha ON drafts(fecha_creacion DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildora_link_from ON pildora_link(from_id, kind)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildora_link_to ON pildora_link(to_id, kind)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_titulo_lower ON pildoras(lower(titulo))");

        }

        try (Statement s = connection.createStatement()) {
            if (!columnExists("pildoras", "protegida")) {
                s.execute("ALTER TABLE pildoras ADD COLUMN protegida INTEGER NOT NULL DEFAULT 0");
            }
            if (!columnExists("pildoras", "descripcion_cipher")) {
                s.execute("ALTER TABLE pildoras ADD COLUMN descripcion_cipher BLOB");
            }
            if (!columnExists("pildoras", "descripcion_iv")) {
                s.execute("ALTER TABLE pildoras ADD COLUMN descripcion_iv BLOB");
            }
        }

        if (isColumnNotNull("pildoras", "descripcion")) {
            boolean oldAuto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement s = connection.createStatement()) {
                s.execute("""
                    CREATE TABLE IF NOT EXISTS pildoras_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        titulo TEXT NOT NULL,
                        descripcion TEXT,
                        fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        fecha_actualizacion TIMESTAMP,
                        favorita INTEGER NOT NULL DEFAULT 0,
                        pinned   INTEGER NOT NULL DEFAULT 0,
                        descripcion_cipher BLOB,
                        descripcion_iv     BLOB,
                        protegida INTEGER NOT NULL DEFAULT 0
                    )
                """);

                if (!columnExists("pildoras", "descripcion_cipher")) {
                    s.execute("ALTER TABLE pildoras ADD COLUMN descripcion_cipher BLOB");
                }
                if (!columnExists("pildoras", "descripcion_iv")) {
                    s.execute("ALTER TABLE pildoras ADD COLUMN descripcion_iv BLOB");
                }
                if (!columnExists("pildoras", "protegida")) {
                    s.execute("ALTER TABLE pildoras ADD COLUMN protegida INTEGER NOT NULL DEFAULT 0");
                }

                s.execute("""
                    INSERT INTO pildoras_new
                      (id, titulo, descripcion, fecha_creacion, fecha_actualizacion,
                       favorita, pinned, descripcion_cipher, descripcion_iv, protegida)
                    SELECT
                      id, titulo, descripcion, fecha_creacion, fecha_actualizacion,
                      favorita, pinned, descripcion_cipher, descripcion_iv, protegida
                    FROM pildoras
                """);

                s.execute("DROP TABLE pildoras");
                s.execute("ALTER TABLE pildoras_new RENAME TO pildoras");

                s.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_favorita ON pildoras(favorita)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_pinned   ON pildoras(pinned)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_protegida ON pildoras(protegida)");

                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(oldAuto);
            }
        }

        dedupeTagsCaseInsensitive();
        try (Statement s = connection.createStatement()) {
            s.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower
                ON tags(lower(nombre))
            """);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        reopenIfNeeded();
        try (Statement s = connection.createStatement();
             var rs = s.executeQuery("PRAGMA table_info('" + table + "')")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(column)) return true;
            }
            return false;
        }
    }

    private boolean isColumnNotNull(String table, String column) throws SQLException {
        reopenIfNeeded();
        try (Statement s = connection.createStatement();
             var rs = s.executeQuery("PRAGMA table_info('" + table + "')")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(column)) {
                    return rs.getInt("notnull") == 1;
                }
            }
            return false;
        }
    }

    private void dedupeTagsCaseInsensitive() throws SQLException {
        reopenIfNeeded();
        boolean oldAuto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement s = connection.createStatement()) {
            try (var rs = s.executeQuery("""
                SELECT COUNT(*) AS dup_grps
                  FROM (
                    SELECT lower(nombre) AS k, COUNT(*) c
                    FROM tags
                    GROUP BY lower(nombre)
                    HAVING COUNT(*) > 1
                  )
            """)) {
                if (rs.next() && rs.getInt("dup_grps") == 0) {
                    s.execute("UPDATE tags SET nombre = lower(nombre)");
                    connection.commit();
                    connection.setAutoCommit(oldAuto);
                    return;
                }
            }

            s.execute("""
                CREATE TEMP TABLE IF NOT EXISTS tag_group AS
                SELECT lower(nombre) AS k, MIN(id) AS keep_id
                FROM tags
                GROUP BY lower(nombre)
            """);

            s.execute("""
                CREATE TEMP TABLE IF NOT EXISTS tag_map AS
                SELECT t.id AS old_id, g.keep_id AS new_id
                FROM tags t
                JOIN tag_group g ON lower(t.nombre) = g.k
            """);

            int ins = s.executeUpdate("""
                INSERT OR IGNORE INTO pildora_tag (pildora_id, tag_id)
                SELECT pt.pildora_id, m.new_id
                FROM pildora_tag pt
                JOIN tag_map m ON pt.tag_id = m.old_id
                WHERE m.old_id <> m.new_id
            """);

            int delPt = s.executeUpdate("""
                DELETE FROM pildora_tag
                WHERE tag_id IN (
                  SELECT old_id FROM tag_map WHERE old_id <> new_id
                )
            """);

            int upd = s.executeUpdate("UPDATE tags SET nombre = lower(nombre)");

            int delTags = s.executeUpdate("""
                DELETE FROM tags
                WHERE id IN (
                  SELECT old_id FROM tag_map WHERE old_id <> new_id
                )
            """);

            s.execute("DROP TABLE IF EXISTS tag_map");
            s.execute("DROP TABLE IF EXISTS tag_group");

            connection.commit();
            System.out.println("[DB][dedupe] insert_pt=" + ins + " delete_pt=" + delPt + " upd_text=" + upd + " del_tags=" + delTags);

        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(oldAuto);
        }
    }
    
 // --- Helpers para sync/snapshot ---
    public synchronized void checkpointNow() {
        try {
            reopenIfNeeded();
            try (Statement s = connection.createStatement()) {
                // Fuerza checkpoint de WAL para snapshot consistente (FULL = mueve el contenido a DB)
                s.execute("PRAGMA wal_checkpoint(FULL)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo ejecutar wal_checkpoint(FULL)", e);
        }
    }

    public Path getDbFile() {
        return getDbPath();
    }
    public Path getWalFile() {
        return getDbPath().resolveSibling(getDbPath().getFileName().toString() + "-wal");
    }
    public Path getShmFile() {
        return getDbPath().resolveSibling(getDbPath().getFileName().toString() + "-shm");
    }
    public Path getDbDir() {
        return getDbPath().getParent();
    }

}