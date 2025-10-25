package io.github.guillermo_david.sync;

public final class SyncState {
    public String deviceId;
    public String lastLocalSnapshotHash;
    public String lastCloudSeenHash;
    public String lastSyncAt;      // ISO-8601
    public String lastConflictAt;  // ISO-8601
    public String policy;
    public String lastDataHash;    // 👈 NUEVO: hash estable de datos
}

