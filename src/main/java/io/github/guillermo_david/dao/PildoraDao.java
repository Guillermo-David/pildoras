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

	public void insertar(Pildora p) {
		String sql = """
				    INSERT INTO pildoras
				      (titulo, descripcion, fecha_creacion, fecha_actualizacion,
				       favorita, pinned, descripcion_cipher, descripcion_iv, protegida)
				    VALUES (?, ?, CURRENT_TIMESTAMP, NULL, 0, 0, ?, ?, ?)
				""";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql,
				Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, p.getTitulo());
			if (p.isProtegida())
				ps.setNull(2, java.sql.Types.VARCHAR);
			else
				ps.setString(2, p.getDescripcion());
			if (p.isProtegida())
				ps.setBytes(3, p.getDescripcionCipher());
			else
				ps.setNull(3, java.sql.Types.BLOB);
			if (p.isProtegida())
				ps.setBytes(4, p.getDescripcionIv());
			else
				ps.setNull(4, java.sql.Types.BLOB);
			ps.setInt(5, p.isProtegida() ? 1 : 0);

			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next())
					p.setId(rs.getInt(1));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error insertando píldora", e);
		}
	}

	public List<Pildora> listarFiltradas(String tituloFiltro, String tagsFiltro, boolean andMode, boolean soloFavoritas,
			int page, int pageSize, String columnaOrden, String direccionOrden) {

// Whitelist para ORDER BY
		String colOrden = switch (columnaOrden) {
		case "titulo" -> "lower(p.titulo)";
		case "descripcion" -> "lower(p.descripcion)";
		case "fecha_creacion" -> "p.fecha_creacion";
		case "fecha_actualizacion" -> "p.fecha_actualizacion";
		case "favorita" -> "p.favorita";
		default -> "p.fecha_creacion";
		};
		String dir = "ASC".equalsIgnoreCase(direccionOrden) ? "ASC" : "DESC";

		StringBuilder sql = new StringBuilder("""
				SELECT
				  p.id,
				  p.titulo,
				  p.descripcion,
				  p.fecha_creacion,
				  p.fecha_actualizacion,
				  p.favorita,
				  p.pinned,
				  p.protegida,
				  p.descripcion_cipher,
				  p.descripcion_iv
				FROM pildoras p
				""");

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
			sql.append(", p.fecha_creacion DESC ");
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
					
					String descripcion = rs.getString("descripcion");
					
					if (rs.getInt("protegida") == 1) {
						descripcion = "Contenido protegido";
					}
					lista.add(new Pildora(rs.getInt("id"), rs.getString("titulo"), descripcion,
							rs.getTimestamp("fecha_creacion") != null
									? rs.getTimestamp("fecha_creacion").toLocalDateTime()
									: null,
							rs.getTimestamp("fecha_actualizacion") != null
									? rs.getTimestamp("fecha_actualizacion").toLocalDateTime()
									: null,
							rs.getInt("favorita") == 1, 
							rs.getInt("pinned") == 1, 
							rs.getInt("protegida") == 1,
							rs.getBytes("descripcion_cipher"), 
							rs.getBytes("descripcion_iv")));
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
		String sql = 
				"""
				SELECT 
					id, 
					titulo, 
					descripcion, 
					fecha_creacion, 
					fecha_actualizacion, 
					favorita, 
					pinned, 
					protegida, 
					descripcion_cipher, 
					descripcion_iv 
					FROM pildoras WHERE id = ?
					""";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {

			pstmt.setInt(1, id);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return new Pildora(rs.getInt("id"), rs.getString("titulo"), rs.getString("descripcion"),
							rs.getString("fecha_creacion") != null
									? LocalDateTime.parse(rs.getString("fecha_creacion"), FORMATTER)
									: null,
							rs.getString("fecha_actualizacion") != null
									? LocalDateTime.parse(rs.getString("fecha_actualizacion"), FORMATTER)
									: null,
							rs.getInt("favorita") == 1 ? true : false, rs.getInt("pinned") == 1 ? true : false,
							rs.getInt("protegida") == 1 ? true : false, rs.getBytes("descripcion_cipher"),
							rs.getBytes("descripcion_iv"));
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public List<Pildora> buscarPorTitulo(String q, int limit) {
        String sql = """
            SELECT id, titulo, fecha_creacion, fecha_actualizacion, favorita, pinned, protegida
            FROM pildoras
            WHERE (? IS NULL OR ? = '' OR lower(titulo) LIKE lower(?) )
            ORDER BY COALESCE(fecha_actualizacion, fecha_creacion) DESC, id DESC
            LIMIT ?
            """;
        List<Pildora> out = new java.util.ArrayList<>();
        try (var cx = DatabaseHelper.getInstance().getConnection();
             var ps = cx.prepareStatement(sql)) {
            String like = (q == null || q.isBlank()) ? "" : "%" + q.trim() + "%";
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, like);
            ps.setInt(4, Math.max(1, limit));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pildora p = new Pildora();
                    p.setId(rs.getInt("id"));
                    p.setTitulo(rs.getString("titulo"));
                    p.setFavorita(rs.getInt("favorita") == 1);
                    p.setPinned(rs.getInt("pinned") == 1);
                    p.setProtegida(rs.getInt("protegida") == 1);
                    var fc = rs.getTimestamp("fecha_creacion");
                    var fa = rs.getTimestamp("fecha_actualizacion");
                    if (fc != null) p.setFechaCreacion(fc.toLocalDateTime());
                    if (fa != null) p.setFechaActualizacion(fa.toLocalDateTime());
                    out.add(p);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error buscando píldoras", ex);
        }
        return out;
    }

	public void actualizar(Pildora p) {
		String sql = """
				    UPDATE pildoras
				    SET titulo = ?,
				        descripcion = ?,
				        fecha_actualizacion = ?,
				        favorita = ?, pinned = ?,
				        descripcion_cipher = ?, descripcion_iv = ?,
				        protegida = ?
				    WHERE id = ?
				""";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setString(1, p.getTitulo());

			if (p.isProtegida())
				ps.setNull(2, java.sql.Types.VARCHAR);
			else
				ps.setString(2, p.getDescripcion());

			ps.setString(3, LocalDateTime.now().format(FORMATTER));
			ps.setInt(4, p.isFavorita() ? 1 : 0);
			ps.setInt(5, p.isPinned() ? 1 : 0);

			if (p.isProtegida()) {
				ps.setBytes(6, p.getDescripcionCipher());
				ps.setBytes(7, p.getDescripcionIv());
			} else {
				ps.setNull(6, java.sql.Types.BLOB);
				ps.setNull(7, java.sql.Types.BLOB);
			}

			ps.setInt(8, p.isProtegida() ? 1 : 0);
			ps.setInt(9, p.getId());

			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Error actualizando píldora", e);
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
