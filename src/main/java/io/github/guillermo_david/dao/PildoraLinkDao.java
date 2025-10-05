package io.github.guillermo_david.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Pildora;

public class PildoraLinkDao {

	public void replaceRefs(int fromId, Collection<Integer> toIds) {
	    final String del = "DELETE FROM pildora_link WHERE from_id=? AND kind='ref'";
	    final String ins = "INSERT OR IGNORE INTO pildora_link(from_id, to_id, kind) VALUES(?, ?, 'ref')";

	    Connection cx = DatabaseHelper.getInstance().getConnection();

	    boolean newTx = true;
	    java.sql.Savepoint sp = null;

	    try {
	        // Si ya hay transacción activa (autoCommit=false), usa savepoint; si no, inicia transacción nueva
	        newTx = cx.getAutoCommit();
	        if (newTx) {
	            cx.setAutoCommit(false);
	        } else {
	            sp = cx.setSavepoint();
	        }

	        try (java.sql.PreparedStatement psDel = cx.prepareStatement(del);
	             java.sql.PreparedStatement psIns = cx.prepareStatement(ins)) {

	            psDel.setInt(1, fromId);
	            psDel.executeUpdate();

	            if (toIds != null) {
	                for (Integer to : toIds) {
	                    if (to == null || to == fromId) continue; // evita auto-link
	                    psIns.setInt(1, fromId);
	                    psIns.setInt(2, to);
	                    psIns.addBatch();
	                }
	                psIns.executeBatch();
	            }
	        }

	        // Commit / libera savepoint
	        if (newTx) {
	            cx.commit();
	        } else if (sp != null) {
	            cx.releaseSavepoint(sp);
	        }

	    } catch (java.sql.SQLException e) {
	        // Rollback parcial o total según el caso
	        try {
	            if (newTx) {
	                cx.rollback();
	            } else if (sp != null) {
	                cx.rollback(sp);
	            }
	        } catch (java.sql.SQLException ignored) {}
	        throw new RuntimeException("Error sincronizando enlaces", e);
	    } finally {
	        // Restaura autoCommit si lo tocaste
	        try {
	            if (newTx) cx.setAutoCommit(true);
	        } catch (java.sql.SQLException ignored) {}
	    }
	}


    /** Enlaces SALIENTES (from -> to), devuelve Pildora mínima (id,titulo,protegida). */
    public List<Pildora> listOutgoingRefs(int fromId) {
        String sql = """
            SELECT p.id, p.titulo, p.protegida
            FROM pildora_link l
            JOIN pildoras p ON p.id = l.to_id
            WHERE l.from_id = ? AND l.kind = 'ref'
            ORDER BY lower(p.titulo)
        """;
        List<Pildora> out = new ArrayList<>();
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, fromId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pildora p = new Pildora();
                    p.setId(rs.getInt("id"));
                    p.setTitulo(rs.getString("titulo"));
                    p.setProtegida(rs.getInt("protegida") == 1);
                    out.add(p);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    /** Enlaces ENTRANTES (other -> toId), devuelve la Pildora origen. */
    public List<Pildora> listIncomingRefs(int toId) {
        String sql = """
            SELECT p.id, p.titulo, p.protegida
            FROM pildora_link l
            JOIN pildoras p ON p.id = l.from_id
            WHERE l.to_id = ? AND l.kind = 'ref'
            ORDER BY lower(p.titulo)
        """;
        List<Pildora> out = new ArrayList<>();
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, toId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pildora p = new Pildora();
                    p.setId(rs.getInt("id"));
                    p.setTitulo(rs.getString("titulo"));
                    p.setProtegida(rs.getInt("protegida") == 1);
                    out.add(p);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }
    
//    public List<Pildora> listarSalientes(int fromId) {
//        String sql = """
//            SELECT p.id, p.titulo
//            FROM pildora_link l
//            JOIN pildoras p ON p.id = l.to_id
//            WHERE l.from_id = ? AND l.kind = 'ref'
//            ORDER BY COALESCE(l.position, 999999), p.titulo
//        """;
//
//        Connection cx = DatabaseHelper.getInstance().getConnection();
//        
//        try (var ps = cx.prepareStatement(sql)) {
//            ps.setInt(1, fromId);
//            try (var rs = ps.executeQuery()) {
//                var out = new ArrayList<Pildora>();
//                while (rs.next()) {
//                    var p = new Pildora();
//                    p.setId(rs.getInt("id"));
//                    p.setTitulo(rs.getString("titulo"));
//                    out.add(p);
//                }
//                return out;
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public List<Pildora> listarEntrantes(int toId) {
//        String sql = """
//            SELECT p.id, p.titulo
//            FROM pildora_link l
//            JOIN pildoras p ON p.id = l.from_id
//            WHERE l.to_id = ? AND l.kind = 'ref'
//            ORDER BY p.titulo
//        """;
//        
//        Connection cx = DatabaseHelper.getInstance().getConnection();
//        
//        try (var ps = cx.prepareStatement(sql)) {
//            ps.setInt(1, toId);
//            try (var rs = ps.executeQuery()) {
//                var out = new java.util.ArrayList<io.github.guillermo_david.model.Pildora>();
//                while (rs.next()) {
//                    var p = new io.github.guillermo_david.model.Pildora();
//                    p.setId(rs.getInt("id"));
//                    p.setTitulo(rs.getString("titulo"));
//                    out.add(p);
//                }
//                return out;
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
    
}
