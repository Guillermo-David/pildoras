package io.github.guillermo_david.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    public static Path dataDir() {
        String appdata = System.getenv("APPDATA"); // Roaming
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Paths.get(appdata, "Pildoras");
    }

    public static Path dbPath() {
        return dataDir().resolve("knowledgebase.db");
    }

    public static void ensureDataDir() throws IOException {
        Files.createDirectories(dataDir());
    }
    
    public static Path syncStateFile() {
        return dataDir().resolve("sync_state.json");
    }
    public static Path logsDir() {
        return dataDir().resolve("logs");
    }
    public static Path syncLogFile() {
        return logsDir().resolve("sync.log");
    }

}
