package io.github.guillermo_david.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseHelper {

    private static DatabaseHelper instance;
    private Connection connection;

    private DatabaseHelper() {
        try {
            Path dbPath = getDatabasePath();
            migrateOldLocationIfNeeded(dbPath);
            Files.createDirectories(dbPath.getParent());

            String url = "jdbc:sqlite:" + dbPath.toString();
            connection = DriverManager.getConnection(url);

            initializeDatabase();
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error inicializando la base de datos", e);
        }
    }

    public static synchronized DatabaseHelper getInstance() {
        if (instance == null) {
            instance = new DatabaseHelper();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private Path getDatabasePath() {
        // Usamos %APPDATA%\Pildoras\knowledgebase.db
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Paths.get(appdata, "Pildoras", "knowledgebase.db");
    }

    private void migrateOldLocationIfNeeded(Path nueva) throws IOException {
        if (Files.exists(nueva)) return; // ya migrada

        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) return;

        Path antigua = Paths.get(local, "Pildoras", "knowledgebase.db");
        if (Files.exists(antigua)) {
            Files.createDirectories(nueva.getParent());
            Files.move(antigua, nueva, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pildoras (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo TEXT NOT NULL,
                    descripcion TEXT NOT NULL,
                    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    fecha_actualizacion TIMESTAMP,
                    favorita INTEGER NOT NULL DEFAULT 0,
                    pinned INTEGER NOT NULL DEFAULT 0
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
        }
    }
}
