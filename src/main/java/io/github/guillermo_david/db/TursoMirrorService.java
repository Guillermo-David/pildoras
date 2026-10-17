package io.github.guillermo_david.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.guillermo_david.dao.SqlHelper;

/** Copia periódica Turso -> SQLite local (solo lectura) para poder trabajar sin conexión. */
public final class TursoMirrorService {

    private static final long PERIOD_MINUTES = 10;
    private static ScheduledExecutorService scheduler;

    private TursoMirrorService() {}

    public static synchronized void startBackgroundSync() {
        if (scheduler != null)
            return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "turso-mirror");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                sync();
            } catch (Exception e) {
                System.err.println("[Mirror] Sincronización fallida: " + e.getMessage());
            }
        }, 5, PERIOD_MINUTES * 60, TimeUnit.SECONDS);
    }

    public static synchronized void stopBackgroundSync() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static void sync() throws Exception {
        if (!TursoConfig.isConfigured())
            return;
        String url = TursoConfig.toJdbcUrl(TursoConfig.getUrl());
        try (Connection turso = DriverManager.getConnection(url, null, TursoConfig.getToken())) {
            syncFrom(turso, DatabaseHelper.getMirrorPath());
        }
    }

    static void syncFrom(Connection source, Path mirror) throws Exception {
        Files.createDirectories(mirror.getParent());
        Path tmp = mirror.resolveSibling(mirror.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        try (Connection target = DriverManager.getConnection("jdbc:sqlite:" + tmp)) {
            DatabaseHelper.initSchema(target);
            target.setAutoCommit(false);
            try {
                copyAll(source, target);
                target.commit();
            } catch (Exception e) {
                target.rollback();
                throw e;
            }
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        try {
            Files.move(tmp, mirror, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, mirror, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("[Mirror] Espejo actualizado: " + mirror);
    }

    static void copyAll(Connection source, Connection target) throws SQLException {
        copy(source, target, "SELECT id, nombre FROM tags",
                "INSERT INTO tags(id, nombre) VALUES(?, ?)", "iS");
        copy(source, target,
                "SELECT id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned, "
                        + "hex(descripcion_cipher) AS c, hex(descripcion_iv) AS v, protegida, oculta, eliminada, "
                        + "fecha_eliminacion FROM pildoras",
                "INSERT INTO pildoras(id, titulo, descripcion, fecha_creacion, fecha_actualizacion, favorita, pinned, "
                        + "descripcion_cipher, descripcion_iv, protegida, oculta, eliminada, fecha_eliminacion) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "iSSSSiiBBiiiS");
        copy(source, target, "SELECT pildora_id, tag_id FROM pildora_tag",
                "INSERT OR IGNORE INTO pildora_tag(pildora_id, tag_id) VALUES(?, ?)", "ii");
        copy(source, target, "SELECT from_id, to_id, kind FROM pildora_link",
                "INSERT OR IGNORE INTO pildora_link(from_id, to_id, kind) VALUES(?, ?, ?)", "iiS");
        copy(source, target,
                "SELECT id, titulo, contenido, hex(contenido_cipher) AS c, hex(contenido_iv) AS v, protegida, "
                        + "fecha_creacion, fecha_actualizacion FROM drafts",
                "INSERT INTO drafts(id, titulo, contenido, contenido_cipher, contenido_iv, protegida, "
                        + "fecha_creacion, fecha_actualizacion) VALUES(?,?,?,?,?,?,?,?)",
                "iSSBBiSS");
    }

    /** types: i = int, S = string, B = blob leído como hex. */
    private static void copy(Connection source, Connection target, String select, String insert, String types)
            throws SQLException {
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery(select);
             PreparedStatement ps = target.prepareStatement(insert)) {
            while (rs.next()) {
                for (int i = 0; i < types.length(); i++) {
                    int col = i + 1;
                    switch (types.charAt(i)) {
                        case 'i' -> ps.setInt(col, rs.getInt(col));
                        case 'S' -> {
                            String v = rs.getString(col);
                            if (v == null) ps.setObject(col, null); else ps.setString(col, v);
                        }
                        default -> {
                            byte[] b = SqlHelper.hexToBytes(rs.getString(col));
                            if (b == null) ps.setObject(col, null); else ps.setBytes(col, b);
                        }
                    }
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
