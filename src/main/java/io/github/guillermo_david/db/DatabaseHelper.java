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
    private static final String DB_NAME    = "knowledgebase.db";

    private static DatabaseHelper instance;
    private Connection connection;

    private DatabaseHelper() {
        try {
            Path target = getDbPath();
            migrateOldLocationIfNeeded(target);           // mueve/copias si procede
            Files.createDirectories(target.getParent());

            String url = "jdbc:sqlite:" + target.toString();
            connection = DriverManager.getConnection(url);

            // PRAGMAs recomendables
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA busy_timeout=5000");
            }

            initializeDatabase();

            // DEBUG útil
            System.out.println("[DB] APPDATA=" + System.getenv("APPDATA"));
            System.out.println("[DB] LOCALAPPDATA=" + System.getenv("LOCALAPPDATA"));
            System.out.println("[DB] user.dir=" + System.getProperty("user.dir"));
            System.out.println("[DB] DB_PATH=" + DatabaseHelper.getDatabasePathForDebug());
            System.out.println("[DB] JDBC URL=" + url);

        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error inicializando la base de datos", e);
        }
    }

    public static synchronized DatabaseHelper getInstance() {
        if (instance == null) instance = new DatabaseHelper();
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    // --- Paths ---

    /** Devuelve %APPDATA% (solo el directorio Roaming base). */
    private static Path getRoamingDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Paths.get(appdata).toAbsolutePath().normalize();
    }

    /** Devuelve %LOCALAPPDATA% (solo el directorio Local base). */
    private static Path getLocalDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Paths.get(local);
        // Fallback: %USERPROFILE%\AppData\Local
        Path home = Paths.get(System.getProperty("user.home"));
        return home.resolve("AppData").resolve("Local");
    }

    /** Ruta completa al fichero de BD en Roaming: %APPDATA%\Pildoras\knowledgebase.db */
    private static Path getDbPath() {
        return getRoamingDir().resolve(APP_FOLDER).resolve(DB_NAME)
                              .toAbsolutePath().normalize();
    }

    /** Para logs: ruta completa al fichero de BD. */
    public static Path getDatabasePathForDebug() {
        return getDbPath();
    }

    private static void migrateOldLocationIfNeeded(Path dstDb) throws IOException {
        Path dstDir = dstDb.getParent();
        Files.createDirectories(dstDir);

        // 1) Si ya hay BD en Roaming y tiene contenido, no tocar
        if (Files.exists(dstDb) && Files.size(dstDb) > 0) return;

        // 2) Origen antiguo (LocalAppData)
        Path srcDb = getLocalDir().resolve(APP_FOLDER).resolve(DB_NAME);
        if (!Files.exists(srcDb)) {
            // No hay nada que migrar. Si dst no existe, más tarde se creará schema nuevo.
            return;
        }

        // 3) Si el destino existe pero está vacío, lo sustituimos.
        boolean dstExists = Files.exists(dstDb);
        if (dstExists && Files.size(dstDb) == 0) {
            // opcional: backup del vacío si quieres
            // Files.move(dstDb, dstDb.resolveSibling(DB_NAME + ".bak"), REPLACE_EXISTING);
        }

        try {
            Files.move(srcDb, dstDb, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(srcDb, dstDb, StandardCopyOption.REPLACE_EXISTING);
            // opcional: borrar el antiguo si quieres
            // Files.deleteIfExists(srcDb);
        }

        // 4) Migrar también -wal y -shm si existen
        moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-wal"),
                             dstDb.resolveSibling(DB_NAME + "-wal"));
        moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-shm"),
                             dstDb.resolveSibling(DB_NAME + "-shm"));
    }

    private static void moveSiblingIfPresent(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) return;
        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            // opcional: Files.deleteIfExists(src);
        }
    }

    // --- Esquema + migraciones idempotentes ---

    private void initializeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pildoras (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo TEXT NOT NULL,
                    descripcion TEXT NOT NULL,
                    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    fecha_actualizacion TIMESTAMP
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
                    tag_id INTEGER NOT NULL,
                    PRIMARY KEY (pildora_id, tag_id),
                    FOREIGN KEY (pildora_id) REFERENCES pildoras(id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE RESTRICT
                )
            """);

            // --- Migraciones (no fallan si ya existen) ---
            try { stmt.execute("ALTER TABLE pildoras ADD COLUMN favorita INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE pildoras ADD COLUMN pinned   INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignore) {}

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_favorita ON pildoras(favorita)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_pinned   ON pildoras(pinned)");
        }
    }
}
