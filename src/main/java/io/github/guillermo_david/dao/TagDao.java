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

	public Tag findOrCreate(String input) {
	    String nombre = normalize(input);
	    if (nombre.isEmpty()) return null;

	    var conn = DatabaseHelper.getInstance().getConnection();

	    String select = "SELECT id, nombre FROM tags WHERE lower(nombre) = ?";
	    try (PreparedStatement ps = conn.prepareStatement(select)) {
	        ps.setString(1, nombre);
	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) return new Tag(rs.getInt("id"), rs.getString("nombre"));
	        }
	    } catch (SQLException e) {
	        throw new RuntimeException("Error buscando tag", e);
	    }

	    String insert = "INSERT INTO tags (nombre) VALUES (?)";
	    try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
	        ps.setString(1, nombre);
	        ps.executeUpdate();
	        try (ResultSet rs = ps.getGeneratedKeys()) {
	            if (rs.next()) return new Tag(rs.getInt(1), nombre); // devuelve normalizado
	        }
	    } catch (SQLException e) {
	        // posible carrera: re-lee
	        try (PreparedStatement ps2 = conn.prepareStatement(select)) {
	            ps2.setString(1, nombre);
	            try (ResultSet rs2 = ps2.executeQuery()) {
	                if (rs2.next()) return new Tag(rs2.getInt("id"), rs2.getString("nombre"));
	            }
	        } catch (SQLException ignore) {}
	        throw new RuntimeException("Error insertando tag", e);
	    }

	    return null;
	}

	private static String normalize(String s) {
	    return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
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
    
    public List<String> listAllLike(String prefix, int limit) {
        String sql = """
            SELECT t.nombre
            FROM tags t
            WHERE lower(t.nombre) LIKE lower(?) || '%'
            ORDER BY t.nombre
            LIMIT ?
            """;
        ArrayList<String> out = new ArrayList<>();
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setString(1, prefix == null ? "" : prefix.trim());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

}
