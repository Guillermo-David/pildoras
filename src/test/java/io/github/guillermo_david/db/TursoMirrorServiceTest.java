package io.github.guillermo_david.db;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

class TursoMirrorServiceTest {

    @Test
    void mirrorCopiesAllTablesAndBlobs() throws Exception {
        Path dir = Files.createTempDirectory("mirror");
        try (Connection src = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("src.db"))) {
            DatabaseHelper.initSchema(src);
            try (var s = src.createStatement()) {
                s.execute("INSERT INTO tags(id, nombre) VALUES (1, 'x')");
                s.execute("INSERT INTO pildoras(id, titulo, descripcion, oculta, eliminada, fecha_eliminacion) "
                        + "VALUES (1, 'uno', 'texto', 1, 0, NULL)");
                s.execute("INSERT INTO pildoras(id, titulo, protegida, descripcion_cipher, descripcion_iv, eliminada, fecha_eliminacion) "
                        + "VALUES (2, 'dos', 1, X'00FF10', X'0A0B', 1, '2026-01-01 00:00:00')");
                s.execute("INSERT INTO pildora_tag VALUES (1, 1)");
                s.execute("INSERT INTO pildora_link(from_id, to_id, kind) VALUES (1, 2, 'ref')");
                s.execute("INSERT INTO drafts(id, titulo, contenido) VALUES (1, 'borrador', 'c')");
            }
            Path mirror = dir.resolve("mirror.db");
            TursoMirrorService.syncFrom(src, mirror);

            try (Connection m = DriverManager.getConnection("jdbc:sqlite:" + mirror);
                 var s = m.createStatement()) {
                try (var rs = s.executeQuery("SELECT titulo, oculta, eliminada FROM pildoras WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("uno", rs.getString(1));
                    assertEquals(1, rs.getInt(2));
                }
                try (var rs = s.executeQuery(
                        "SELECT descripcion_cipher, descripcion_iv, eliminada, fecha_eliminacion FROM pildoras WHERE id = 2")) {
                    assertTrue(rs.next());
                    assertArrayEquals(new byte[] { 0, (byte) 0xFF, 0x10 }, rs.getBytes(1));
                    assertArrayEquals(new byte[] { 0x0A, 0x0B }, rs.getBytes(2));
                    assertEquals(1, rs.getInt(3));
                    assertEquals("2026-01-01 00:00:00", rs.getString(4));
                }
                for (String t : new String[] { "tags", "pildora_tag", "pildora_link", "drafts" }) {
                    try (var rs = s.executeQuery("SELECT COUNT(*) FROM " + t)) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1), t);
                    }
                }
                try (var rs = s.executeQuery("SELECT rowid FROM pildoras_fts WHERE pildoras_fts MATCH 'uno'")) {
                    assertTrue(rs.next());
                }
            }
            assertFalse(Files.exists(dir.resolve("mirror.db.tmp")));
        }
    }
}
