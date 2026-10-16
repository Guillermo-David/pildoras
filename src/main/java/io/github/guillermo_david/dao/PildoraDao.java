package io.github.guillermo_david.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static io.github.guillermo_david.dao.SqlHelper.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Pildora;

public class PildoraDao {

	public void insertar(Pildora p) {
		if (DatabaseHelper.getInstance().isUsingTurso()) {
			insertarTurso(p);
			return;
		}
		String sql = """
				    INSERT INTO pildoras
				      (titulo, descripcion, fecha_creacion, fecha_actualizacion,
				       favorita, pinned, descripcion_cipher, descripcion_iv, protegida, oculta)
				    VALUES (?, ?, CURRENT_TIMESTAMP, NULL, 0, 0, ?, ?, ?, ?)
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
			ps.setInt(6, p.isOculta() ? 1 : 0);

			ps.execute();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next())
					p.setId(rs.getInt(1));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error insertando píldora", e);
		}
	}

	private void insertarTurso(Pildora p) {
		String desc = p.isProtegida() ? "NULL" : esc(p.getDescripcion());
		String cipher = p.isProtegida() ? escBlob(p.getDescripcionCipher()) : "NULL";
		String iv = p.isProtegida() ? escBlob(p.getDescripcionIv()) : "NULL";
		String sql = "INSERT INTO pildoras (titulo, descripcion, fecha_creacion, fecha_actualizacion,"
				+ " favorita, pinned, descripcion_cipher, descripcion_iv, protegida, oculta)"
				+ " VALUES (" + esc(p.getTitulo()) + ", " + desc + ", CURRENT_TIMESTAMP, NULL, 0, 0, "
				+ cipher + ", " + iv + ", " + (p.isProtegida() ? 1 : 0)
				+ ", " + (p.isOculta() ? 1 : 0) + ")";
		try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement()) {
			st.execute(sql);
			try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
				if (rs.next()) p.setId(rs.getInt(1));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error insertando píldora", e);
		}
	}

	public List<Pildora> listarFiltradas(String tituloFiltro, String tagsFiltro, boolean andMode, boolean soloFavoritas,
			int page, int pageSize, String columnaOrden, String direccionOrden, boolean mostrarOcultas) {

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
				  hex(p.descripcion_cipher) AS cipher_hex,
				  hex(p.descripcion_iv)     AS iv_hex,
				  p.oculta
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

		if (!mostrarOcultas) {
			sql.append("AND p.oculta = 0 ");
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
					Pildora pil = new Pildora(rs.getInt("id"), rs.getString("titulo"), descripcion,
							parseDateTime(rs.getString("fecha_creacion")),
							parseDateTime(rs.getString("fecha_actualizacion")),
							rs.getInt("favorita") == 1,
							rs.getInt("pinned") == 1,
							rs.getInt("protegida") == 1,
							hexToBytes(rs.getString("cipher_hex")),
							hexToBytes(rs.getString("iv_hex")),
							rs.getInt("oculta") == 1);
					lista.add(pil);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return lista;
	}

	public int contarFiltradas(String tituloFiltro, String tagsFiltro, boolean andMode, boolean soloFavoritas, boolean mostrarOcultas) {
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

		if (!mostrarOcultas) {
			sql.append("AND p.oculta = 0 ");
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
					id, titulo, descripcion,
					fecha_creacion, fecha_actualizacion,
					favorita, pinned, protegida,
					hex(descripcion_cipher) AS cipher_hex,
					hex(descripcion_iv)     AS iv_hex,
					oculta
				FROM pildoras WHERE id = ?
				""";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, id);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					Pildora p = new Pildora(rs.getInt("id"), rs.getString("titulo"), rs.getString("descripcion"),
							parseDateTime(rs.getString("fecha_creacion")),
							parseDateTime(rs.getString("fecha_actualizacion")),
							rs.getInt("favorita") == 1, rs.getInt("pinned") == 1,
							rs.getInt("protegida") == 1,
							hexToBytes(rs.getString("cipher_hex")),
							hexToBytes(rs.getString("iv_hex")),
							rs.getInt("oculta") == 1);
					return p;
				}
			}
		} catch (Exception e) {
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
                    p.setFechaCreacion(parseDateTime(rs.getString("fecha_creacion")));
                    p.setFechaActualizacion(parseDateTime(rs.getString("fecha_actualizacion")));
                    out.add(p);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error buscando píldoras", ex);
        }
        return out;
    }

	public void actualizar(Pildora p) {
		if (DatabaseHelper.getInstance().isUsingTurso()) {
			actualizarTurso(p);
			return;
		}
		String sql = """
				    UPDATE pildoras
				    SET titulo = ?,
				        descripcion = ?,
				        fecha_actualizacion = ?,
				        favorita = ?, pinned = ?,
				        descripcion_cipher = ?, descripcion_iv = ?,
				        protegida = ?,
				        oculta = ?
				    WHERE id = ?
				""";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setString(1, p.getTitulo());

			if (p.isProtegida())
				ps.setNull(2, java.sql.Types.VARCHAR);
			else
				ps.setString(2, p.getDescripcion());

			ps.setString(3, LocalDateTime.now().format(DATETIME_FMT));
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
			ps.setInt(9, p.isOculta() ? 1 : 0);
			ps.setInt(10, p.getId());

			ps.execute();
		} catch (SQLException e) {
			throw new RuntimeException("Error actualizando píldora", e);
		}
	}

	private void actualizarTurso(Pildora p) {
		String desc = p.isProtegida() ? "NULL" : esc(p.getDescripcion());
		String cipher = p.isProtegida() ? escBlob(p.getDescripcionCipher()) : "NULL";
		String iv = p.isProtegida() ? escBlob(p.getDescripcionIv()) : "NULL";
		String ts = LocalDateTime.now().format(DATETIME_FMT);
		String sql = "UPDATE pildoras SET titulo=" + esc(p.getTitulo())
				+ ", descripcion=" + desc
				+ ", fecha_actualizacion=" + esc(ts)
				+ ", favorita=" + (p.isFavorita() ? 1 : 0)
				+ ", pinned=" + (p.isPinned() ? 1 : 0)
				+ ", descripcion_cipher=" + cipher
				+ ", descripcion_iv=" + iv
				+ ", protegida=" + (p.isProtegida() ? 1 : 0)
				+ ", oculta=" + (p.isOculta() ? 1 : 0)
				+ " WHERE id=" + p.getId();
		try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement()) {
			st.execute(sql);
		} catch (SQLException e) {
			throw new RuntimeException("Error actualizando píldora", e);
		}
	}

	public void eliminar(int id) {
		String sql = "DELETE FROM pildoras WHERE id = ?";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, id);
			pstmt.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void marcarFavorita(long id, boolean favorita) {
		String sql = "UPDATE pildoras SET favorita = ? WHERE id = ?";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setInt(1, favorita ? 1 : 0);
			ps.setLong(2, id);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void marcarPinned(long id, boolean pinned) {
		String sql = "UPDATE pildoras SET pinned = ? WHERE id = ?";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setInt(1, pinned ? 1 : 0);
			ps.setLong(2, id);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void marcarOculta(long id, boolean oculta) {
		String sql = "UPDATE pildoras SET oculta = ? WHERE id = ?";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setInt(1, oculta ? 1 : 0);
			ps.setLong(2, id);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}
