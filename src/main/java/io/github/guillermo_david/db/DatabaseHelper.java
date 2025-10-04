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

	private DatabaseHelper() {
		try {
			Path target = getDbPath();
			migrateOldLocationIfNeeded(target); // mueve/copias si procede
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
		if (instance == null)
			instance = new DatabaseHelper();
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
		if (local != null && !local.isBlank())
			return Paths.get(local);
		// Fallback: %USERPROFILE%\AppData\Local
		Path home = Paths.get(System.getProperty("user.home"));
		return home.resolve("AppData").resolve("Local");
	}

	/**
	 * Ruta completa al fichero de BD en Roaming:
	 * %APPDATA%\Pildoras\knowledgebase.db
	 */
	private static Path getDbPath() {
		return getRoamingDir().resolve(APP_FOLDER).resolve(DB_NAME).toAbsolutePath().normalize();
	}

	/** Para logs: ruta completa al fichero de BD. */
	public static Path getDatabasePathForDebug() {
		return getDbPath();
	}

	private static void migrateOldLocationIfNeeded(Path dstDb) throws IOException {
		Path dstDir = dstDb.getParent();
		Files.createDirectories(dstDir);

		// 1) Si ya hay BD en Roaming y tiene contenido, no tocar
		if (Files.exists(dstDb) && Files.size(dstDb) > 0)
			return;

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
		moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-wal"), dstDb.resolveSibling(DB_NAME + "-wal"));
		moveSiblingIfPresent(srcDb.resolveSibling(DB_NAME + "-shm"), dstDb.resolveSibling(DB_NAME + "-shm"));
	}

	private static void moveSiblingIfPresent(Path src, Path dst) throws IOException {
		if (!Files.exists(src))
			return;
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
	        // Crea tablas base si no existen (ya con el esquema nuevo)
	        stmt.execute("""
	            CREATE TABLE IF NOT EXISTS pildoras (
	                id INTEGER PRIMARY KEY AUTOINCREMENT,
	                titulo TEXT NOT NULL,
	                descripcion TEXT,                             -- ahora permite NULL
	                fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	                fecha_actualizacion TIMESTAMP,
	                favorita INTEGER NOT NULL DEFAULT 0,
	                pinned   INTEGER NOT NULL DEFAULT 0,
	                descripcion_cipher BLOB,                      -- puede ser NULL
	                descripcion_iv     BLOB,                      -- puede ser NULL
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

	        // Índices básicos
	        stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_favorita ON pildoras(favorita)");
	        stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_pinned   ON pildoras(pinned)");
	        stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_protegida ON pildoras(protegida)");
	        stmt.execute("CREATE INDEX IF NOT EXISTS idx_drafts_fecha ON drafts(fecha_creacion DESC)");
	    }

	    // --- MIGRACIONES IDEMPOTENTES ---

	    // 1) Añade columnas que falten (si la BD es antigua)
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

	    // 2) Si 'descripcion' está marcada NOT NULL, reconstruye la tabla para permitir NULL
	    if (isColumnNotNull("pildoras", "descripcion")) {
	        // reconstrucción en transacción
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

	            // Asegúrate de que las columnas existen antes del volcado
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

	    // 3) Deduplicar tags y crear índice único case-insensitive (como ya tenías)
	    dedupeTagsCaseInsensitive();
	    try (Statement s = connection.createStatement()) {
	        s.execute("""
	            CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower
	            ON tags(lower(nombre))
	        """);
	    }
	}

	/** Devuelve true si la tabla contiene la columna dada (case-insensitive). */
	private boolean columnExists(String table, String column) throws SQLException {
	    try (Statement s = connection.createStatement();
	         var rs = s.executeQuery("PRAGMA table_info('" + table + "')")) {
	        while (rs.next()) {
	            String name = rs.getString("name");
	            if (name != null && name.equalsIgnoreCase(column)) return true;
	        }
	        return false;
	    }
	}

	/** Devuelve true si la columna está marcada NOT NULL en el esquema actual. */
	private boolean isColumnNotNull(String table, String column) throws SQLException {
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
	    // Transacción
	    boolean oldAuto = connection.getAutoCommit();
	    connection.setAutoCommit(false);
	    try (Statement s = connection.createStatement()) {

	        // 0) Si no hay duplicados, salir rápido (opcional pero útil)
	        //    Duplicado = mismo lower(nombre) con más de 1 id
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
	                // Asegura normalización por si llegan mayúsculas sueltas
	                s.execute("UPDATE tags SET nombre = lower(nombre)");
	                connection.commit();
	                connection.setAutoCommit(oldAuto);
	                return;
	            }
	        }

	        // 1) Para cada grupo case-insensitive decide un id "canónico" (mínimo id)
	        s.execute("""
	            CREATE TEMP TABLE IF NOT EXISTS tag_group AS
	            SELECT lower(nombre) AS k, MIN(id) AS keep_id
	            FROM tags
	            GROUP BY lower(nombre)
	        """);

	        // 2) Mapeo old_id -> new_id (canónico)
	        s.execute("""
	            CREATE TEMP TABLE IF NOT EXISTS tag_map AS
	            SELECT t.id AS old_id, g.keep_id AS new_id
	            FROM tags t
	            JOIN tag_group g ON lower(t.nombre) = g.k
	        """);

	        // 3) Re-crear los enlaces con el id canónico SIN romper la PK (pildora_id, tag_id)
	        //    (Insertamos primero y luego borramos los viejos)
	        int ins = s.executeUpdate("""
	            INSERT OR IGNORE INTO pildora_tag (pildora_id, tag_id)
	            SELECT pt.pildora_id, m.new_id
	            FROM pildora_tag pt
	            JOIN tag_map m ON pt.tag_id = m.old_id
	            WHERE m.old_id <> m.new_id
	        """);

	        // 4) Eliminar los enlaces antiguos (los que iban al old_id duplicado)
	        int delPt = s.executeUpdate("""
	            DELETE FROM pildora_tag
	            WHERE tag_id IN (
	              SELECT old_id FROM tag_map WHERE old_id <> new_id
	            )
	        """);

	        // 5) Normalizar el texto a minúsculas
	        int upd = s.executeUpdate("UPDATE tags SET nombre = lower(nombre)");

	        // 6) Borrar los tags duplicados (conservar solo el id canónico)
	        int delTags = s.executeUpdate("""
	            DELETE FROM tags
	            WHERE id IN (
	              SELECT old_id FROM tag_map WHERE old_id <> new_id
	            )
	        """);

	        // 7) Limpiar temporales
	        s.execute("DROP TABLE IF EXISTS tag_map");
	        s.execute("DROP TABLE IF EXISTS tag_group");

	        connection.commit();

	        // (Opcional) logs para verificar en consola lo que pasó
	        System.out.println("[DB][dedupe] insert_pt=" + ins + " delete_pt=" + delPt + " upd_text=" + upd + " del_tags=" + delTags);

	    } catch (SQLException ex) {
	        connection.rollback();
	        throw ex;
	    } finally {
	        connection.setAutoCommit(oldAuto);
	    }
	}


}
