package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Draft;

public class DraftDao {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void insertar(Draft d) {
        if (DatabaseHelper.getInstance().isUsingTurso()) {
            insertarTurso(d);
            return;
        }
        String sql = """
            INSERT INTO drafts
              (titulo, contenido, contenido_cipher, contenido_iv, protegida,
               fecha_creacion, fecha_actualizacion)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
        """;
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.getTitulo());
            if (d.isProtegida()) {
                ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setBytes(3, d.getContenidoCipher());
                ps.setBytes(4, d.getContenidoIv());
            } else {
                ps.setString(2, d.getContenido());
                ps.setNull(3, java.sql.Types.BLOB);
                ps.setNull(4, java.sql.Types.BLOB);
            }
            ps.setInt(5, d.isProtegida() ? 1 : 0);

            ps.execute();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) d.setId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando borrador", e);
        }
    }

    private void insertarTurso(Draft d) {
        String contenido = d.isProtegida() ? "NULL" : esc(d.getContenido());
        String cipher = d.isProtegida() ? escBlob(d.getContenidoCipher()) : "NULL";
        String iv = d.isProtegida() ? escBlob(d.getContenidoIv()) : "NULL";
        String sql = "INSERT INTO drafts (titulo, contenido, contenido_cipher, contenido_iv, protegida,"
                + " fecha_creacion, fecha_actualizacion)"
                + " VALUES (" + esc(d.getTitulo()) + ", " + contenido + ", " + cipher + ", " + iv
                + ", " + (d.isProtegida() ? 1 : 0) + ", CURRENT_TIMESTAMP, NULL)";
        try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement()) {
            st.execute(sql);
            try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) d.setId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando borrador", e);
        }
    }

    public void actualizar(Draft d) {
        if (DatabaseHelper.getInstance().isUsingTurso()) {
            actualizarTurso(d);
            return;
        }
        String sql = """
            UPDATE drafts SET
              titulo = ?,
              contenido = ?,
              contenido_cipher = ?,
              contenido_iv = ?,
              protegida = ?,
              fecha_actualizacion = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setString(1, d.getTitulo());
            if (d.isProtegida()) {
                ps.setNull(2, java.sql.Types.VARCHAR);
                ps.setBytes(3, d.getContenidoCipher());
                ps.setBytes(4, d.getContenidoIv());
            } else {
                ps.setString(2, d.getContenido());
                ps.setNull(3, java.sql.Types.BLOB);
                ps.setNull(4, java.sql.Types.BLOB);
            }
            ps.setInt(5, d.isProtegida() ? 1 : 0);
            ps.setString(6, LocalDateTime.now().format(FMT));
            ps.setInt(7, d.getId());

            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando borrador", e);
        }
    }

    private void actualizarTurso(Draft d) {
        String contenido = d.isProtegida() ? "NULL" : esc(d.getContenido());
        String cipher = d.isProtegida() ? escBlob(d.getContenidoCipher()) : "NULL";
        String iv = d.isProtegida() ? escBlob(d.getContenidoIv()) : "NULL";
        String ts = LocalDateTime.now().format(FMT);
        String sql = "UPDATE drafts SET titulo=" + esc(d.getTitulo())
                + ", contenido=" + contenido
                + ", contenido_cipher=" + cipher
                + ", contenido_iv=" + iv
                + ", protegida=" + (d.isProtegida() ? 1 : 0)
                + ", fecha_actualizacion=" + esc(ts)
                + " WHERE id=" + d.getId();
        try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando borrador", e);
        }
    }

    public void eliminar(int id) {
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection()
                .prepareStatement("DELETE FROM drafts WHERE id=?")) {
            ps.setInt(1, id);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando borrador", e);
        }
    }

    public Draft buscarPorId(int id) {
        String sql = """
            SELECT id,titulo,contenido,contenido_cipher,contenido_iv,protegida,
                   fecha_creacion,fecha_actualizacion
            FROM drafts WHERE id=?
        """;
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando borrador", e);
        }
    }

    public List<Draft> listarTodos() {
        String sql = """
            SELECT id,titulo,contenido,contenido_cipher,contenido_iv,protegida,
                   fecha_creacion,fecha_actualizacion
            FROM drafts
            ORDER BY fecha_creacion DESC
        """;
        List<Draft> out = new ArrayList<>();
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error listando borradores", e);
        }
        return out;
    }

    public int contar() {
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection()
                .prepareStatement("SELECT COUNT(*) AS c FROM drafts");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("c") : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private static String esc(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("'", "''") + "'";
    }

    private static String escBlob(byte[] val) {
        if (val == null) return "NULL";
        StringBuilder sb = new StringBuilder("X'");
        for (byte b : val) sb.append(String.format("%02x", b));
        sb.append('\'');
        return sb.toString();
    }

    private static Draft map(ResultSet rs) throws SQLException {
        LocalDateTime fc = rs.getString("fecha_creacion") != null
                ? LocalDateTime.parse(rs.getString("fecha_creacion").replace('T', ' '), FMT)
                : null;
        LocalDateTime fa = rs.getString("fecha_actualizacion") != null
                ? LocalDateTime.parse(rs.getString("fecha_actualizacion").replace('T', ' '), FMT)
                : null;
        return new Draft(
            rs.getInt("id"),
            rs.getString("titulo"),
            rs.getString("contenido"),
            rs.getBytes("contenido_cipher"),
            rs.getBytes("contenido_iv"),
            rs.getInt("protegida") == 1,
            fc, fa
        );
    }
}
