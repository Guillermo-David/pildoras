package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Pildora;

public class PildoraDao {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public void insertar(Pildora pildora) {
		String sql = """
				INSERT INTO pildoras (titulo, descripcion, fecha_actualizacion)
				VALUES (?, ?, ?)
				""";

		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql,
				Statement.RETURN_GENERATED_KEYS)) {

			pstmt.setString(1, pildora.getTitulo());
			pstmt.setString(2, pildora.getDescripcion());
			pstmt.setString(3, LocalDateTime.now().format(FORMATTER));

			pstmt.executeUpdate();

			try (ResultSet rs = pstmt.getGeneratedKeys()) {
				if (rs.next()) {
					pildora.setId(rs.getInt(1));
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public List<Pildora> listarFiltradas(String tituloFiltro, String tagsFiltro, boolean andMode, boolean soloFavoritas,
			int page, int pageSize, String columnaOrden, String direccionOrden) {

// Whitelist para ORDER BY
		String colOrden = switch (columnaOrden) {
		case "titulo" -> "p.titulo";
		case "descripcion" -> "p.descripcion";
		case "fecha_creacion" -> "p.fecha_creacion";
		case "fecha_actualizacion" -> "p.fecha_actualizacion";
		case "favorita" -> "p.favorita";
		default -> "p.fecha_creacion";
		};
		String dir = "ASC".equalsIgnoreCase(direccionOrden) ? "ASC" : "DESC";

		StringBuilder sql = new StringBuilder(
				"SELECT DISTINCT p.id, p.titulo, p.descripcion, p.fecha_creacion, p.fecha_actualizacion, p.favorita, p.pinned " + // 👈
																														// favorita
						"FROM pildoras p ");

		boolean joinTags = tagsFiltro != null && !tagsFiltro.isBlank() && !andMode; // OR requiere JOIN
		if (joinTags) {
			sql.append("JOIN pildora_tag pt ON p.id = pt.pildora_id ").append("JOIN tags t ON pt.tag_id = t.id ");
		}

		sql.append("WHERE 1=1 ");

		if (tituloFiltro != null && !tituloFiltro.isBlank()) {
			sql.append("AND (p.titulo LIKE ? OR p.descripcion LIKE ?) ");
		}

		String[] tagsArr = null;
		if (tagsFiltro != null && !tagsFiltro.isBlank()) {
			tagsArr = Arrays.stream(tagsFiltro.split(",")).map(String::trim).filter(s -> !s.isBlank())
					.toArray(String[]::new);
			if (tagsArr.length > 0) {
				if (andMode) {
					for (int i = 0; i < tagsArr.length; i++) {
						sql.append("""
								AND EXISTS (
								 SELECT 1 FROM pildora_tag pt2
								 JOIN tags t2 ON pt2.tag_id = t2.id
								 WHERE pt2.pildora_id = p.id AND LOWER(t2.nombre) = LOWER(?)
								) """);
					}
				} else {
					sql.append("AND LOWER(t.nombre) IN (")
							.append(String.join(", ", Collections.nCopies(tagsArr.length, "?"))).append(") ");
				}
			}
		}

		if (soloFavoritas) {
			sql.append("AND p.favorita = 1 ");
		}

		sql.append(" ORDER BY p.pinned DESC ");

		if (!"p.pinned".equals(colOrden)) {
		    sql.append(", ").append(colOrden).append(' ').append(dir).append(' ');
		} else {
		    // Si el usuario “ordena por pinned”, mantenemos pinned primero
		    // y metemos un criterio estable secundario (por ejemplo, fecha desc)
		    sql.append(", p.titulo DESC ");
		}
// ORDER: favoritas primero (si NO está el filtro exclusivo), luego la columna elegida
//		if (!soloFavoritas && !"p.favorita".equals(colOrden)) {
//			sql.append("ORDER BY p.favorita DESC, ").append(colOrden).append(" ").append(dir).append(" ");
//		} else {
//			sql.append("ORDER BY ").append(colOrden).append(" ").append(dir).append(" ");
//		}

		sql.append("LIMIT ? OFFSET ?");

		List<Pildora> lista = new ArrayList<>();
		try (PreparedStatement stmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql.toString())) {
			int idx = 1;

			if (tituloFiltro != null && !tituloFiltro.isBlank()) {
				String like = "%" + tituloFiltro + "%";
				stmt.setString(idx++, like);
				stmt.setString(idx++, like);
			}
			if (tagsArr != null) {
				for (String t : tagsArr)
					stmt.setString(idx++, t.toLowerCase());
			}

			stmt.setInt(idx++, pageSize);
			stmt.setInt(idx++, Math.max(0, (page - 1) * pageSize));

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					lista.add(new Pildora(
							rs.getInt("id"), 
							rs.getString("titulo"), 
							rs.getString("descripcion"),
							rs.getTimestamp("fecha_creacion") != null
//									? LocalDateTime.parse(rs.getString("fecha_creacion").replace(" ", "T"))
									? rs.getTimestamp("fecha_creacion").toLocalDateTime()
									: null,
							rs.getTimestamp("fecha_actualizacion") != null
									? rs.getTimestamp("fecha_actualizacion").toLocalDateTime()
//									? LocalDateTime.parse(rs.getString("fecha_actualizacion").replace(" ", "T"))
									: null,
							rs.getInt("favorita") == 1,
							rs.getInt("pinned") == 1
							));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return lista;
	}

	public int contarFiltradas(String tituloFiltro, String tagsFiltro, boolean andMode, boolean soloFavoritas) {
		StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT p.id) AS total " + "FROM pildoras p ");

		boolean joinTags = tagsFiltro != null && !tagsFiltro.isBlank() && !andMode; // OR necesita JOIN
		if (joinTags) {
			sql.append("JOIN pildora_tag pt ON p.id = pt.pildora_id ").append("JOIN tags t ON pt.tag_id = t.id ");
		}

		sql.append("WHERE 1=1 ");

		if (tituloFiltro != null && !tituloFiltro.isBlank()) {
			sql.append("AND (p.titulo LIKE ? OR p.descripcion LIKE ?) ");
		}

		String[] tagsArr = null;
		if (tagsFiltro != null && !tagsFiltro.isBlank()) {
			tagsArr = Arrays.stream(tagsFiltro.split(",")).map(String::trim).filter(s -> !s.isBlank())
					.toArray(String[]::new);
			if (tagsArr.length > 0) {
				if (andMode) {
					for (int i = 0; i < tagsArr.length; i++) {
						sql.append("""
								AND EXISTS (
								  SELECT 1 FROM pildora_tag pt2
								  JOIN tags t2 ON pt2.tag_id = t2.id
								  WHERE pt2.pildora_id = p.id AND LOWER(t2.nombre) = LOWER(?)
								) """);
					}
				} else {
					sql.append("AND LOWER(t.nombre) IN (")
							.append(String.join(", ", Collections.nCopies(tagsArr.length, "?"))).append(") ");
				}
			}
		}

		if (soloFavoritas) {
			sql.append("AND p.favorita = 1 ");
		}

		try (PreparedStatement stmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql.toString())) {
			int idx = 1;
			if (tituloFiltro != null && !tituloFiltro.isBlank()) {
				String like = "%" + tituloFiltro + "%";
				stmt.setString(idx++, like);
				stmt.setString(idx++, like);
			}
			if (tagsArr != null) {
				for (String t : tagsArr)
					stmt.setString(idx++, t.toLowerCase());
			}

			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next() ? rs.getInt("total") : 0;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}
	}

	public Pildora buscarPorId(int id) {
		String sql = "SELECT id, titulo, descripcion, fecha_creacion, fecha_actualizacion FROM pildoras WHERE id = ?";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {

			pstmt.setInt(1, id);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return new Pildora(
							rs.getInt("id"), 
							rs.getString("titulo"), 
							rs.getString("descripcion"),
							rs.getString("fecha_creacion") != null
									? LocalDateTime.parse(rs.getString("fecha_creacion"), FORMATTER)
									: null,
							rs.getString("fecha_actualizacion") != null
									? LocalDateTime.parse(rs.getString("fecha_actualizacion"), FORMATTER)
									: null,
							rs.getInt("favorita") == 1 ? true : false,
							rs.getInt("pinned") == 1 ? true : false
									);
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void actualizar(Pildora p) {
		String sql = """
				UPDATE pildoras
				SET titulo = ?, descripcion = ?, fecha_actualizacion = CURRENT_TIMESTAMP
				WHERE id = ?
				""";

		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			pstmt.setString(1, p.getTitulo());
			pstmt.setString(2, p.getDescripcion());
			pstmt.setInt(3, p.getId());
			pstmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void eliminar(int id) {
		String sql = "DELETE FROM pildoras WHERE id = ?";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, id);
			pstmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void marcarFavorita(long id, boolean favorita) {
	    String sql = "UPDATE pildoras SET favorita = ? WHERE id = ?";
	    try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
	        ps.setInt(1, favorita ? 1 : 0);
	        ps.setLong(2, id);
	        ps.executeUpdate();
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

	public void marcarPinned(long id, boolean pinned) {
	    String sql = "UPDATE pildoras SET pinned = ? WHERE id = ?";
	    try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
	        ps.setInt(1, pinned ? 1 : 0);
	        ps.setLong(2, id);
	        ps.executeUpdate();
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

}
