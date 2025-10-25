package io.github.guillermo_david.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.guillermo_david.db.AppPaths;
import io.github.guillermo_david.db.DatabaseHelper;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SyncService {

    public enum Mode { MANUAL, AUTO_ON_START, AUTO_ON_CLOSE }

    private static final SyncService SINGLETON = new SyncService();
    public static SyncService get() { return SINGLETON; }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DatabaseHelper db = DatabaseHelper.getInstance();

    // Config “v1”: carpeta fija en G:
    private final Path driveRoot = Paths.get("G:/Mi unidad/Pildoras/backups").toAbsolutePath().normalize();
    private final Path driveTmp  = driveRoot.resolve("_tmp");
    private final Path driveHist = driveRoot.resolve("hist");

    // Estado local
    private SyncState state;

    private SyncService() {
        try {
            Files.createDirectories(AppPaths.logsDir());
            Files.createDirectories(driveTmp);
            Files.createDirectories(driveHist);
            Files.createDirectories(driveRoot);
            this.state = loadState();
            if (this.state == null) {
                this.state = new SyncState();
                this.state.deviceId = defaultDeviceId();
                saveState();
            } else if (this.state.deviceId == null || this.state.deviceId.isBlank()) {
                this.state.deviceId = defaultDeviceId();
                saveState();
            }
        } catch (IOException e) {
            throw new RuntimeException("SyncService init error", e);
        }
    }

    public void onAppStart() {
        // v1: solo aviso si cloud > local (ofrecer restaurar fuera de este stub)
        try {
        	var cloudLatest = DriveBackend.findMostRecentLatest(driveRoot);
            if (cloudLatest.isEmpty()) return;

            var localHash = state.lastLocalSnapshotHash;
            var cloudManifest = Manifest.readFromZip(cloudLatest.get());

            // Si nunca hemos sincronizado localmente o cloud es más nuevo por timestamp → notificar
            if (cloudManifest != null && (localHash == null || isCloudAhead(cloudManifest))) {
                logInfo("[SYNC] Cloud más reciente: " + cloudManifest.timestampUtc + " (device=" + cloudManifest.deviceId + ")");
                // Aquí podrías abrir diálogo de restauración. En v1 vamos un paso seguro: no restauramos automáticamente.
            }
        } catch (Exception ex) {
            logError("[SYNC] Error en onAppStart: " + ex.getMessage());
        } finally {
            ensureBaselineHistoryIfEmpty();
        }
    }
    
 // SyncService
    private void ensureBaselineHistoryIfEmpty() {
        try {
            // ¿ya hay algún hist para este device?
            var hasHist = Files.list(driveHist)
                .anyMatch(p -> p.getFileName().toString()
                    .matches("pildoras-\\d{8}-\\d{6}-" + state.deviceId + "-[a-fA-F0-9]{8}(?:_CONFLICT)?\\.zip"));
            if (hasHist) return;

            // si no hay hist y existe latest, copiamos uno como baseline
            var latestZip = driveRoot.resolve("pildoras-latest-" + state.deviceId + ".zip");
            if (Files.exists(latestZip)) {
                var mf = Manifest.readFromZip(latestZip);
                if (mf != null) {
                    var histName = DriveBackend.histName(mf, false);
                    var histZip  = driveHist.resolve(histName);
                    Files.copy(latestZip, histZip, StandardCopyOption.REPLACE_EXISTING);
                    logInfo("[SYNC] Baseline creado en hist: " + histZip.getFileName());
                }
            }
            boolean hasLatest = Files.exists(latestZip);
            if (!hasHist && !hasLatest) {
                logInfo("[SYNC] No hay latest ni hist ⇒ creando baseline…");
                syncNow(Mode.MANUAL);
            }
            
        } catch (Exception e) {
            logWarn("[SYNC] No se pudo crear baseline en hist: " + e.getMessage());
        }
    }

    public void onAppShutdown() {
        try {
            syncNow(Mode.AUTO_ON_CLOSE);
        } catch (Exception ex) {
            logError("[SYNC] Error en onAppShutdown: " + ex.getMessage());
        }
    }

    public SyncResult syncNow(Mode mode) {
        Objects.requireNonNull(mode, "mode");
        try {
            logInfo("[SYNC] Preparando snapshot (" + mode + ")…");

            // 0) Asegura estado mínimo
            var deviceId = state.deviceId;

            // 1) Checkpoint para consolidar WAL en DB
            db.checkpointNow();

            // 2) Rutas y tiempos
            Path dbFile = db.getDbFile();
            Path wal    = db.getWalFile();
            Path shm    = db.getShmFile();

            boolean dbExists = Files.exists(dbFile);
            if (!dbExists) {
                logWarn("[SYNC] DB no existe en " + dbFile + " — nada que hacer.");
                return SyncResult.ok(null, null);
            }

            FileTime dbMtime = Files.getLastModifiedTime(dbFile);
            Instant dbTs = dbMtime.toInstant();
            Instant lastSyncTs = null;
            try { lastSyncTs = (state.lastSyncAt == null || state.lastSyncAt.isBlank()) ? null : Instant.parse(state.lastSyncAt); } catch (Exception ignore) {}

            // 3) HASH ESTABLE de datos (DB + WAL + SHM presentes)
            String dataHash = Integrity.sha256Concat(
                    java.util.stream.Stream.of(dbFile, wal, shm)
                            .filter(p -> p != null && Files.exists(p))
                            .toArray(Path[]::new)
            );

            // 4) Comparación contra "lo último que conocemos"
            Path latestZipDisk = driveRoot.resolve("pildoras-latest-" + deviceId + ".zip");
            boolean latestExists = Files.exists(latestZipDisk);

            String prevDataHash = state.lastDataHash; // puede ser null si venimos “limpios”
            String latestDataHash = null;
            if (latestExists) {
                var latestMf = Manifest.readFromZip(latestZipDisk);
                if (latestMf != null) latestDataHash = latestMf.dataSha256;
            }
            String effectivePrev = (prevDataHash != null) ? prevDataHash : latestDataHash;

            // 4.b) Heurística de cambio por tiempo de modificación (fallback)
            // Si la DB se modificó tras el último sync conocido, consideramos que hay cambios.
            boolean mtimeSuggestsChange = (lastSyncTs != null) && dbTs.isAfter(lastSyncTs.plusMillis(1));

            logInfo("[SYNC] dataHash=" + dataHash +
                    " prev=" + (effectivePrev == null ? "null" : effectivePrev) +
                    " latestExists=" + latestExists +
                    " dbTs=" + dbTs + " lastSyncTs=" + lastSyncTs +
                    " mtimeSuggestsChange=" + mtimeSuggestsChange);

            // 4.c) Decisión “sin cambios”
            if (latestExists && effectivePrev != null && effectivePrev.equals(dataHash) && !mtimeSuggestsChange) {
                logInfo("[SYNC] Sin cambios en datos y existe latest ⇒ no generamos ZIP.");
                return SyncResult.ok(null, null);
            }

            // 5) Crear ZIP temporal con manifest (incluye dataSha256)
            var tmpZip = SnapshotCreator.createZipToTmp(db, driveTmp, deviceId, dataHash);

            // 6) Leer manifest del ZIP temporal
            var manifest = Manifest.readFromZip(tmpZip);
            if (manifest == null) throw new IllegalStateException("Manifest no encontrado en ZIP temporal");

            // 7) Conflicto simple: cloud cambió "justo ahora"
            var cloudLatestOpt = DriveBackend.findMostRecentLatest(driveRoot);
            if (cloudLatestOpt.isPresent()) {
                var cloudManifest = Manifest.readFromZip(cloudLatestOpt.get());
                if (cloudManifest != null && isCloudAhead(cloudManifest) && state.lastLocalSnapshotHash != null) {
                    var conflictName = DriveBackend.histName(manifest, /*conflict*/true);
                    var conflictZip = driveHist.resolve(conflictName);
                    Files.move(tmpZip, conflictZip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    logWarn("[SYNC] Conflicto detectado. Guardado local como " + conflictZip.getFileName());
                    state.lastConflictAt = manifest.timestampUtc;
                    saveState();
                    return SyncResult.conflict(conflictZip);
                }
            }

            // 8) Promocionar a latest (move atómico)
            var latestZip = driveRoot.resolve("pildoras-latest-" + deviceId + ".zip");
            FileUtils.moveAtomic(tmpZip, latestZip);

            // 9) Copia rotativa a hist
            var histName = DriveBackend.histName(manifest, false);
            var histZip  = driveHist.resolve(histName);
            Files.copy(latestZip, histZip, StandardCopyOption.REPLACE_EXISTING);

            // 10) Actualizar estado local
            state.lastLocalSnapshotHash = manifest.sha256Zip;
            state.lastCloudSeenHash = manifest.sha256Zip;
            state.lastSyncAt = manifest.timestampUtc;
            state.lastDataHash = dataHash;
            saveState();

            // 11) Rotación
            RetentionPolicy.rotate(driveHist, deviceId);

            logInfo("[SYNC] Snapshot publicado como latest y archivado en hist.");
            return SyncResult.ok(latestZip, histZip);

        } catch (Exception ex) {
            logError("[SYNC] Error en syncNow: " + ex.getMessage());
            ex.printStackTrace();
            return SyncResult.error(ex);
        }
    }

    private boolean isCloudAhead(Manifest cloud) {
        try {
            var localTs = Optional.ofNullable(state.lastSyncAt).orElse("");
            if (localTs.isBlank()) return true;
            var c = Instant.parse(cloud.timestampUtc);
            var l = Instant.parse(localTs);
            return c.isAfter(l);
        } catch (Exception e) {
            return true;
        }
    }

    // --- Estado local ---

    private SyncState loadState() {
        var p = AppPaths.syncStateFile();
        try {
            if (!Files.exists(p)) return null;
            try (var r = Files.newBufferedReader(p)) {
                return gson.fromJson(r, SyncState.class);
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    private void saveState() {
        var p = AppPaths.syncStateFile();
        try {
            Files.createDirectories(p.getParent());
            try (var w = Files.newBufferedWriter(p)) {
                gson.toJson(state, w);
            }
        } catch (IOException ignore) {}
    }

    private static String defaultDeviceId() {
        var env = System.getenv("COMPUTERNAME");
        if (env == null || env.isBlank()) env = System.getProperty("user.name", "DEVICE").toUpperCase(Locale.ROOT);
        return env.replaceAll("[^A-Z0-9_-]", "_");
    }

    private static void logInfo(String s){ System.out.println(s); }
    private static void logWarn(String s){ System.out.println(s); }
    private static void logError(String s){ System.err.println(s); }

    // --- Tipos resultado ---

    public static final class SyncResult {
        public final boolean ok;
        public final boolean conflict;
        public final Path latestZip;
        public final Path histZip;
        public final Exception error;

        private SyncResult(boolean ok, boolean conflict, Path latestZip, Path histZip, Exception error) {
            this.ok = ok; this.conflict = conflict; this.latestZip = latestZip; this.histZip = histZip; this.error = error;
        }
        public static SyncResult ok(Path latestZip, Path histZip){ return new SyncResult(true,false,latestZip,histZip,null); }
        public static SyncResult conflict(Path histZip){ return new SyncResult(false,true,null,histZip,null); }
        public static SyncResult error(Exception e){ return new SyncResult(false,false,null,null,e); }
    }
}
