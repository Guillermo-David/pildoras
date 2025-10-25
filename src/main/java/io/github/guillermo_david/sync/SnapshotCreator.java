package io.github.guillermo_david.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.guillermo_david.db.DatabaseHelper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

public final class SnapshotCreator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Crea ZIP en _tmp con DB (+WAL/+SHM si existen) y manifest.json. Devuelve ruta del ZIP. */
    public static Path createZipToTmp(DatabaseHelper db, Path tmpDir, String deviceId, String dataSha256) throws IOException {
        Files.createDirectories(tmpDir);

        var tmpWork = Files.createTempDirectory(tmpDir, "work-");
        try {
            var dbFile = db.getDbFile();
            var wal    = db.getWalFile();
            var shm    = db.getShmFile();

            Files.copy(dbFile, tmpWork.resolve(dbFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(wal)) Files.copy(wal, tmpWork.resolve(wal.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(shm)) Files.copy(shm, tmpWork.resolve(shm.getFileName()), StandardCopyOption.REPLACE_EXISTING);

            // Manifest v1
            var mf = new Manifest();
            mf.versionSchema = 1;
            mf.timestampUtc = java.time.Instant.now().toString();
            mf.deviceId = deviceId;
            mf.dataSha256 = dataSha256;  // 👈 hash estable de datos

            var manifestJson = tmpWork.resolve("manifest.json");
            try (var w = java.nio.file.Files.newBufferedWriter(manifestJson)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mf, w);
            }

            // ZIP #1
            var zipName = "pildoras-tmp-" + deviceId + "-" + System.currentTimeMillis() + ".zip";
            var tmpZip  = tmpDir.resolve(zipName);
            Zipper.zipDirectory(tmpWork, tmpZip);

            // Completar mf con tamaño y sha del ZIP, y reempaquetar (igual que antes)
            mf.sha256Zip = Integrity.sha256Concat(tmpZip);
            mf.sizeBytes = java.nio.file.Files.size(tmpZip);

            var tmpWork2 = java.nio.file.Files.createTempDirectory(tmpDir, "work2-");
            try {
                // re-materializamos para meter el manifest con campos completos
                Files.copy(dbFile, tmpWork2.resolve(dbFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                if (Files.exists(wal)) Files.copy(wal, tmpWork2.resolve(wal.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                if (Files.exists(shm)) Files.copy(shm, tmpWork2.resolve(shm.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                try (var w = java.nio.file.Files.newBufferedWriter(tmpWork2.resolve("manifest.json"))) {
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mf, w);
                }
                var finalZip = tmpDir.resolve("pildoras-" + deviceId + "-" + System.currentTimeMillis() + ".zip");
                Zipper.zipDirectory(tmpWork2, finalZip);
                java.nio.file.Files.deleteIfExists(tmpZip);
                return finalZip;
            } finally {
                FileUtils.deleteRecursive(tmpWork2);
            }
        } finally {
            FileUtils.deleteRecursive(tmpWork);
        }
    }

}
