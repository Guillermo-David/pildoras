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
	private boolean usingTurso;

	private DatabaseHelper() {
		try {
			if (TursoConfig.isConfigured()) {
				connectTurso();
			} else {
				connectLocal();
			}

			initializeDatabase();

			System.out.println("[DB] Using Turso: " + usingTurso);
			if (!usingTurso) {
				System.out.println("[DB] DB_PATH=" + getDatabasePathForDebug());
			}

		} catch (SQLException | IOException e) {
			throw new RuntimeException("Error inicializando la base de datos", e);
		}
	}

	private void connectTurso() throws SQLException {
		String jdbcUrl = TursoConfig.toJdbcUrl(TursoConfig.getUrl());
		String token = TursoConfig.getToken();

		connection = DriverManager.getConnection(jdbcUrl, null, token);
		usingTurso = true;

		try (Statement s = connection.createStatement()) {
			s.execute("PRAGMA foreign_keys=ON");
		}

		System.out.println("[DB] Connected to Turso: " + TursoConfig.getUrl());
	}

	private void connectLocal() throws SQLException, IOException {
		Path target = getDbPath();
		migrateOldLocationIfNeeded(target);
		Files.createDirectories(target.getParent());

		String url = "jdbc:sqlite:" + target.toString();
		connection = DriverManager.getConnection(url);
		usingTurso = false;

		try (Statement s = connection.createStatement()) {
			s.execute("PRAGMA foreign_keys=ON");
			s.execute("PRAGMA journal_mode=WAL");
			s.execute("PRAGMA synchronous=NORMAL");
			s.execute("PRAGMA busy_timeout=5000");
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

	public boolean isUsingTurso() {
		return usingTurso;
	}

	public static synchronized void reconnect() {
		if (instance != null) {
			try {
				instance.connection.close();
			} catch (SQLException ignore) {}
			instance = null;
		}
		getInstance();
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
		if (local != null && !local.isBlank())
			return Paths.get(local);
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

		if (Files.exists(dstDb) && Files.size(dstDb) > 0)
			return;

		Path srcDb = getLocalDir().resolve(APP_FOLDER).resolve(DB_NAME);
		if (!Files.exists(srcDb)) {
			return;
		}

		boolean dstExists = Files.exists(dstDb);
		if (dstExists && Files.size(dstDb) == 0) {
			// dst vacío, lo sustituimos
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
		if (!Files.exists(src))
			return;
		try {
			Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ex) {
			Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
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

			try {
				stmt.execute("ALTER TABLE pildoras ADD COLUMN favorita INTEGER NOT NULL DEFAULT 0");
			} catch (SQLException ignore) {
			}
			try {
				stmt.execute("ALTER TABLE pildoras ADD COLUMN pinned   INTEGER NOT NULL DEFAULT 0");
			} catch (SQLException ignore) {
			}

			stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_favorita ON pildoras(favorita)");
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_pildoras_pinned   ON pildoras(pinned)");
		}

		dedupeTagsCaseInsensitive();

		try (Statement s = connection.createStatement()) {
			s.execute("""
					    CREATE UNIQUE INDEX IF NOT EXISTS u_tags_nombre_lower
					    ON tags(lower(nombre))
					""");
		}
	}

	private void dedupeTagsCaseInsensitive() throws SQLException {
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
}
