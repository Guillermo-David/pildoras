package io.github.guillermo_david.settings;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.guillermo_david.db.DatabaseHelper;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Settings current;

    public static final class Settings {
        /** Carpeta base elegida por el usuario (equiv. a “…/Pildoras”). Si null → backups desactivados */
        public String backupsBaseDir;
    }

    private SettingsStore() {}

    public static synchronized Settings load() {
        if (current != null) return current;
        Path dir = DatabaseHelper.getInstance().getDbDir(); // %APPDATA%/Pildoras
        Path file = dir.resolve("settings.json");
        Settings s = new Settings();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    s = GSON.fromJson(r, Settings.class);
                }
            }
        } catch (Exception ignore) {}
        if (s == null) s = new Settings();
        current = s;
        return current;
    }

    public static synchronized void save(Settings s) {
        if (s == null) s = new Settings();
        current = s;
        Path dir = DatabaseHelper.getInstance().getDbDir();
        Path file = dir.resolve("settings.json");
        try {
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(s, w);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar settings.json", e);
        }
    }

    public static Path getBackupsBaseDir() {
        var s = (current != null ? current : load());
        if (s.backupsBaseDir == null || s.backupsBaseDir.isBlank()) return null;
        return Path.of(s.backupsBaseDir);
    }

    public static Path getBackupsRoot() { // “…/Pildoras/backups”
        var base = getBackupsBaseDir();
        return base == null ? null : base.resolve("backups");
    }
}
