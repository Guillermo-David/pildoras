package io.github.guillermo_david.restore;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.guillermo_david.db.DatabaseHelper;

/**
 * Restaura knowledgebase.db (+-wal/-shm si existen) desde un ZIP de backups.
 * Pasos:
 *  - Verifica (si existe) manifest.json → sha256Zip == sha256(real del zip).
 *  - Extrae DB/WAL/SHM a una carpeta temporal.
 *  - Cierra la conexión SQLite, hace backup local, y mueve (REPLACE) los ficheros a %APPDATA%/Pildoras.
 *  - Reabre la conexión (lazily).
 */
public class DriveRestoreService {

    public static final class Result {
        public final boolean verified;
        public final String deviceId;
        public final String timestampUtc;
        public final Path targetDir;

        public Result(boolean verified, String deviceId, String timestampUtc, Path targetDir) {
            this.verified = verified;
            this.deviceId = deviceId;
            this.timestampUtc = timestampUtc;
            this.targetDir = targetDir;
        }
    }

    public Result restoreFromZip(File zipFile, boolean backupExisting) throws Exception {
    if (zipFile == null || !zipFile.isFile()) {
        throw new IllegalArgumentException("Selecciona un ZIP válido.");
    }

    // 1) Intento “best effort” de verificación por sha256 del ZIP
    String sha256Zip = computeSha256Hex(zipFile.toPath());
    JsonObject manifest = readManifest(zipFile);
    boolean verified = false;
    boolean mismatch = false;
    String deviceId = null;
    String tsUtc = null;

    if (manifest != null) {
        String decl = optString(manifest, "sha256Zip");
        deviceId = optString(manifest, "deviceId");
        tsUtc = optString(manifest, "timestampUtc");
        if (decl != null && !decl.isBlank()) {
            verified = decl.equalsIgnoreCase(sha256Zip);
            mismatch = !verified; // SOLO avisamos; no bloquea
        }
    }

    // 2) Extraer a carpeta temporal
    Path tempDir = Files.createTempDirectory("pildoras-restore-");
    Path dbTmp, walTmp, shmTmp;
    try (ZipFile zf = new ZipFile(zipFile)) {
        dbTmp = extractIfPresent(zf, "knowledgebase.db", tempDir);
        if (dbTmp == null) throw new IllegalStateException("El ZIP no contiene knowledgebase.db");
        walTmp = extractIfPresent(zf, "knowledgebase.db-wal", tempDir);
        shmTmp = extractIfPresent(zf, "knowledgebase.db-shm", tempDir);
    }

    // 3) (opcional) si quieres mantener un aviso si el header no cuadra, solo WARN:
    if (!looksLikeSQLite(dbTmp)) {
        System.err.println("[WARN] El header del DB extraído no coincide exactamente con 'SQLite format 3\\0'. " +
                "Continuamos y validaremos con PRAGMA integrity_check.");
    }

    // 4) Cierra DB y haz backup local
    DatabaseHelper db = DatabaseHelper.getInstance();
    Path dstDir = db.getDbDir();
    Path dstDb  = db.getDbFile();
    Path dstWal = db.getWalFile();
    Path dstShm = db.getShmFile();

    db.close(); // libera locks/WAL

    Path bakDb = null, bakWal = null, bakShm = null;
    if (backupExisting && Files.exists(dstDb)) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        bakDb  = dstDir.resolve("knowledgebase.db.bak." + stamp);
        Files.copy(dstDb, bakDb, StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(dstWal)) { bakWal = dstDir.resolve("knowledgebase.db-wal.bak." + stamp); Files.copy(dstWal, bakWal, StandardCopyOption.REPLACE_EXISTING); }
        if (Files.exists(dstShm)) { bakShm = dstDir.resolve("knowledgebase.db-shm.bak." + stamp); Files.copy(dstShm, bakShm, StandardCopyOption.REPLACE_EXISTING); }
    }

    Files.createDirectories(dstDir);

    // 5) Mover ficheros restaurados
    moveReplace(dbTmp, dstDb);
    if (walTmp != null) moveReplace(walTmp, dstWal); else Files.deleteIfExists(dstWal);
    if (shmTmp != null) moveReplace(shmTmp, dstShm); else Files.deleteIfExists(dstShm);

    // 6) Validación fuerte: PRAGMA integrity_check
    boolean ok = false;
    try {
        ok = runIntegrityCheck();
    } catch (Exception ex) {
        ok = false;
    }

    if (!ok) {
        // Revertir a backup si lo había
        if (bakDb != null && Files.exists(bakDb)) {
            moveReplace(bakDb, dstDb);
            if (bakWal != null && Files.exists(bakWal)) moveReplace(bakWal, dstWal); else Files.deleteIfExists(dstWal);
            if (bakShm != null && Files.exists(bakShm)) moveReplace(bakShm, dstShm); else Files.deleteIfExists(dstShm);
        }
        throw new IllegalStateException("La base restaurada no superó PRAGMA integrity_check.");
    }

    // Si llegamos aquí: restauración OK (aunque verified pueda ser false)
    return new Result(verified && !mismatch, deviceId, tsUtc, dstDir);
}

    
    private static boolean looksLikeSQLite(Path dbFile) {
        try (var in = Files.newInputStream(dbFile)) {
            byte[] hdr = new byte[16];
            int r = in.read(hdr);
            if (r < 16) return false;
            // Compara bytes en crudo: "SQLite format 3\0"
            byte[] magic = new byte[] {
                0x53,0x51,0x4C,0x69,0x74,0x65,0x20,0x66,0x6F,0x72,0x6D,0x61,0x74,0x20,0x33,0x00
            };
            for (int i = 0; i < 16; i++) if (hdr[i] != magic[i]) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean runIntegrityCheck() throws Exception {
        var cx = DatabaseHelper.getInstance().getConnection();
        try (var st = cx.createStatement();
             var rs = st.executeQuery("PRAGMA integrity_check;")) {
            if (rs.next()) {
                String s = rs.getString(1);
                return s != null && s.equalsIgnoreCase("ok");
            }
            return false;
        }
    }



    // --- Helpers ---

    private static void moveReplace(Path src, Path dst) throws Exception {
        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path extractIfPresent(ZipFile zf, String name, Path tempDir) throws Exception {
        ZipEntry e = zf.getEntry(name);
        if (e == null) return null;
        Path out = tempDir.resolve(name);
        try (InputStream in = new BufferedInputStream(zf.getInputStream(e))) {
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    private static JsonObject readManifest(File zipFile) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry e = zf.getEntry("manifest.json");
            if (e == null) return null;
            try (InputStream in = zf.getInputStream(e)) {
                return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String optString(JsonObject o, String key) {
        if (o == null || key == null) return null;
        var el = o.get(key);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }

    private static String computeSha256Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, md)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
                // solo para avanzar y calcular el hash
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

}
