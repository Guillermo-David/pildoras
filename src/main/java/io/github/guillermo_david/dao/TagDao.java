package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Tag;

public class TagDao {

    private static final long CACHE_TTL_MS = 5_000;
    private static volatile long cacheTimestamp;
    private static volatile List<String[]> cachedCountsAll;
    private static volatile List<String[]> cachedCountsVisible;
    private static volatile Map<Integer, List<Tag>> cachedByPildora = new ConcurrentHashMap<>();

    public static void invalidateCache() {
        cacheTimestamp = 0;
        cachedCountsAll = null;
        cachedCountsVisible = null;
        cachedByPildora.clear();
    }

	public Tag findOrCreate(String input) {
	    String nombre = normalize(input);
	    if (nombre.isEmpty()) return null;

	    if (DatabaseHelper.getInstance().isUsingTurso()) {
	        return findOrCreateTurso(nombre);
	    }

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
	        ps.execute();
	        try (ResultSet rs = ps.getGeneratedKeys()) {
	            if (rs.next()) return new Tag(rs.getInt(1), nombre);
	        }
	    } catch (SQLException e) {
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

	private Tag findOrCreateTurso(String nombre) {
	    var conn = DatabaseHelper.getInstance().getConnection();

	    String selectSql = "SELECT id, nombre FROM tags WHERE lower(nombre) = " + esc(nombre);
	    try (Statement st = conn.createStatement();
	         ResultSet rs = st.executeQuery(selectSql)) {
	        if (rs.next()) return new Tag(rs.getInt("id"), rs.getString("nombre"));
	    } catch (SQLException e) {
	        throw new RuntimeException("Error buscando tag", e);
	    }

	    String insertSql = "INSERT INTO tags (nombre) VALUES (" + esc(nombre) + ")";
	    try (Statement st = conn.createStatement()) {
	        st.execute(insertSql);
	        try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
	            if (rs.next()) return new Tag(rs.getInt(1), nombre);
	        }
	    } catch (SQLException e) {
	        // race condition: re-read
	        try (Statement st2 = conn.createStatement();
	             ResultSet rs2 = st2.executeQuery(selectSql)) {
	            if (rs2.next()) return new Tag(rs2.getInt("id"), rs2.getString("nombre"));
	        } catch (SQLException ignore) {}
	        throw new RuntimeException("Error insertando tag", e);
	    }

	    return null;
	}

	private static String normalize(String s) {
	    return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String esc(String val) {
	    return SqlHelper.esc(val);
	}

    public void preloadForPildoras(List<Integer> pildoraIds) {
        if (pildoraIds == null || pildoraIds.isEmpty()) return;
        cachedByPildora.clear();
        String sql = "SELECT pt.pildora_id, t.id, t.nombre FROM tags t "
            + "JOIN pildora_tag pt ON t.id = pt.tag_id WHERE pt.pildora_id IN ("
            + String.join(",", java.util.Collections.nCopies(pildoraIds.size(), "?")) + ") ORDER BY t.nombre";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < pildoraIds.size(); i++) ps.setInt(i + 1, pildoraIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cachedByPildora.computeIfAbsent(rs.getInt("pildora_id"), k -> new ArrayList<>())
                        .add(new Tag(rs.getInt("id"), rs.getString("nombre")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        for (int id : pildoraIds) cachedByPildora.putIfAbsent(id, List.of());
        cacheTimestamp = System.currentTimeMillis();
    }

    public List<Tag> findByPildoraId(int pildoraId) {
        List<Tag> cached = cachedByPildora.get(pildoraId);
        if (cached != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) return cached;

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

        cachedByPildora.put(pildoraId, lista);
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

    public List<String[]> listAllWithCount() {
        return listAllWithCount(true);
    }

    public List<String[]> listAllWithCount(boolean includeHidden) {
        List<String[]> hit = includeHidden ? cachedCountsAll : cachedCountsVisible;
        if (hit != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) return hit;

        List<String[]> list = new ArrayList<>();
        String sql;
        if (includeHidden) {
            sql = """
                SELECT t.nombre, COUNT(pt.pildora_id) AS cnt
                FROM tags t
                JOIN pildora_tag pt ON t.id = pt.tag_id
                JOIN pildoras p ON p.id = pt.pildora_id
                WHERE p.eliminada = 0
                GROUP BY t.id, t.nombre
                HAVING cnt > 0
                ORDER BY t.nombre
                """;
        } else {
            sql = """
                SELECT t.nombre, COUNT(pt.pildora_id) AS cnt
                FROM tags t
                JOIN pildora_tag pt ON t.id = pt.tag_id
                JOIN pildoras p ON p.id = pt.pildora_id
                WHERE p.oculta = 0 AND p.eliminada = 0
                GROUP BY t.id, t.nombre
                HAVING cnt > 0
                ORDER BY t.nombre
                """;
        }
        try (Statement stmt = DatabaseHelper.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[]{ rs.getString("nombre"), String.valueOf(rs.getInt("cnt")) });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        if (includeHidden) cachedCountsAll = list; else cachedCountsVisible = list;
        cacheTimestamp = System.currentTimeMillis();
        return list;
    }

    public List<String[]> topTagsByUsage(int limit) {
        String sql = """
            SELECT t.nombre, COUNT(pt.pildora_id) AS cnt
            FROM tags t
            JOIN pildora_tag pt ON t.id = pt.tag_id
            JOIN pildoras p ON p.id = pt.pildora_id
            WHERE p.eliminada = 0
            GROUP BY t.id, t.nombre
            ORDER BY cnt DESC, t.nombre ASC
            LIMIT ?
            """;
        List<String[]> out = new ArrayList<>();
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[]{ rs.getString("nombre"), String.valueOf(rs.getInt("cnt")) });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM tags";
        try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
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
