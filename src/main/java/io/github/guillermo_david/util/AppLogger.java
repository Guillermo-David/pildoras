package io.github.guillermo_david.util;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLogger {

    private static final String LOG_FILE = "pildoras.log";
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Path logDir = getLogDir();
            Files.createDirectories(logDir);
            Path logPath = logDir.resolve(LOG_FILE);

            FileOutputStream fos = new FileOutputStream(logPath.toFile(), true);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            fos.write(("\n\n========== Pildoras started " + ts + " ==========\n").getBytes());

            PrintStream teeOut = new TeePrintStream(System.out, fos);
            PrintStream teeErr = new TeePrintStream(System.err, fos);
            System.setOut(teeOut);
            System.setErr(teeErr);

            System.out.println("[Log] Writing to: " + logPath);
        } catch (Exception e) {
            System.err.println("[Log] Could not initialize log file: " + e.getMessage());
        }
    }

    public static Path getLogPath() {
        return getLogDir().resolve(LOG_FILE);
    }

    private static Path getLogDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            appdata = System.getProperty("user.home");
        }
        return Paths.get(appdata).resolve("Pildoras");
    }

    private static class TeePrintStream extends PrintStream {
        private final PrintStream original;

        TeePrintStream(PrintStream original, OutputStream fileOut) {
            super(fileOut, true);
            this.original = original;
        }

        @Override public void write(int b) {
            super.write(b);
            original.write(b);
        }

        @Override public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            original.write(buf, off, len);
        }

        @Override public void flush() {
            super.flush();
            original.flush();
        }
    }
}
