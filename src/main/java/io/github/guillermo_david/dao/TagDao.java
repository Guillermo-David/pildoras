package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Tag;

public class TagDao {

    public Tag findOrCreate(String nombre) {
        // ¿Ya existe?
        String select = "SELECT id, nombre FROM tags WHERE nombre = ?";
        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(select)) {
            pstmt.setString(1, nombre);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Tag(rs.getInt("id"), rs.getString("nombre"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Si no existe → insertar
        String insert = "INSERT INTO tags (nombre) VALUES (?)";
        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection()
                .prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nombre);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Tag(rs.getInt(1), nombre);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null; // algo falló
    }

    public List<Tag> findByPildoraId(int pildoraId) {
        List<Tag> lista = new ArrayList<>();
        String sql = """
            SELECT t.id, t.nombre
            FROM tags t
            JOIN pildora_tag pt ON t.id = pt.tag_id
            WHERE pt.pildora_id = ?
            """;

        try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, pildoraId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Tag(rs.getInt("id"), rs.getString("nombre")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lista;
    }

    public List<Tag> listarTodos() {
        List<Tag> lista = new ArrayList<>();
        String sql = "SELECT id, nombre FROM tags";

        try (Statement stmt = DatabaseHelper.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(new Tag(rs.getInt("id"), rs.getString("nombre")));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
}
