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

	private record Where(String from, String sql, List<String> params, boolean fts) {
		void bind(PreparedStatement ps, int[] idx) throws SQLException {
			for (String v : params)
				ps.setString(idx[0]++, v);
		}
	}

	/** Cada palabra como prefijo ("tok"*), todas obligatorias. Null si no queda ningún término. */
	static String ftsQuery(String text) {
		List<String> terms = new ArrayList<>();
		for (String raw : text.trim().split("\\s+")) {
			String t = raw.replaceAll("[^\\p{L}\\p{N}_]", "");
			if (!t.isEmpty())
				terms.add("\"" + t + "\"*");
		}
		return terms.isEmpty() ? null : String.join(" ", terms);
	}

	private static Where buildWhere(String tituloFiltro, String tagsFiltro, boolean soloFavoritas,
			boolean mostrarOcultas) {
		StringBuilder w = new StringBuilder("WHERE p.eliminada = 0 ");
		List<String> params = new ArrayList<>();
		String from = "";
		boolean fts = false;

		if (tituloFiltro != null && !tituloFiltro.isBlank()) {
			String match = DatabaseHelper.getInstance().isFtsAvailable() ? ftsQuery(tituloFiltro) : null;
			if (match != null) {
				fts = true;
				from = "JOIN pildoras_fts ON pildoras_fts.rowid = p.id ";
				w.append("AND pildoras_fts MATCH ? ");
				params.add(match);
			} else {
				w.append("AND (p.titulo LIKE ? OR p.descripcion LIKE ?) ");
				String like = "%" + tituloFiltro + "%";
				params.add(like);
				params.add(like);
			}
		}

		if (tagsFiltro != null && !tagsFiltro.isBlank()) {
			String[] tags = Arrays.stream(tagsFiltro.split(",")).map(String::trim).filter(s -> !s.isBlank())
					.map(String::toLowerCase).toArray(String[]::new);
			if (tags.length > 0) {
				for (String t : tags) {
					w.append("""
							AND EXISTS (
							 SELECT 1 FROM pildora_tag pt2
							 JOIN tags t2 ON pt2.tag_id = t2.id
							 WHERE pt2.pildora_id = p.id AND LOWER(t2.nombre) = ?
							) """);
					params.add(t);
				}
			}
		}

		if (soloFavoritas)
			w.append("AND p.favorita = 1 ");
		if (!mostrarOcultas)
			w.append("AND p.oculta = 0 ");
		return new Where(from, w.toString(), params, fts);
	}

	public List<Pildora> listarFiltradas(String tituloFiltro, String tagsFiltro, boolean soloFavoritas,
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

		Where where = buildWhere(tituloFiltro, tagsFiltro, soloFavoritas, mostrarOcultas);
		sql.append(where.from()).append(where.sql());

		sql.append(" ORDER BY p.pinned DESC ");

		boolean defaultOrder = "fecha_creacion".equals(columnaOrden) && "DESC".equals(dir);
		if (where.fts() && defaultOrder) {
			sql.append(", bm25(pildoras_fts, 10.0, 1.0) ");
		} else if (!"p.pinned".equals(colOrden)) {
			sql.append(", ").append(colOrden).append(' ').append(dir).append(' ');
		} else {
			// Si el usuario “ordena por pinned”, mantenemos pinned primero
			// y metemos un criterio estable secundario (por ejemplo, fecha desc)
			sql.append(", p.fecha_creacion DESC ");
		}
		sql.append("LIMIT ? OFFSET ?");

		List<Pildora> lista = new ArrayList<>();
		try (PreparedStatement stmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql.toString())) {
			int[] ix = { 1 };
			where.bind(stmt, ix);
			int idx = ix[0];

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

	public int contarFiltradas(String tituloFiltro, String tagsFiltro, boolean soloFavoritas, boolean mostrarOcultas) {
		Where where = buildWhere(tituloFiltro, tagsFiltro, soloFavoritas, mostrarOcultas);
		String sql = "SELECT COUNT(*) AS total FROM pildoras p " + where.from() + where.sql();

		try (PreparedStatement stmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			where.bind(stmt, new int[] { 1 });
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
            WHERE eliminada = 0 AND (? IS NULL OR ? = '' OR lower(titulo) LIKE lower(?) )
            ORDER BY COALESCE(fecha_actualizacion, fecha_creacion) DESC, id DESC
            LIMIT ?
            """;
        List<Pildora> out = new java.util.ArrayList<>();
        try (var ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
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

	public record TrashItem(int id, String titulo, String fechaEliminacion) {}

	/** Mueve a la papelera. */
	public void eliminar(int id) {
		exec("UPDATE pildoras SET eliminada = 1, fecha_eliminacion = ?, pinned = 0 WHERE id = ?",
				LocalDateTime.now().format(DATETIME_FMT), id);
		TagDao.invalidateCache();
	}

	/** Copia la píldora (contenido, cifrado, etiquetas y enlaces) con un sufijo en el título. */
	public Pildora duplicar(int id, String suffix) {
		Pildora src = buscarPorId(id);
		if (src == null)
			return null;
		Pildora copy = new Pildora(0, src.getTitulo() + suffix, src.getDescripcion(), null, null, false, false,
				src.isProtegida(), src.getDescripcionCipher(), src.getDescripcionIv(), src.isOculta());
		insertar(copy);
		exec("INSERT INTO pildora_tag(pildora_id, tag_id) SELECT ?, tag_id FROM pildora_tag WHERE pildora_id = ?",
				copy.getId(), id);
		exec("INSERT OR IGNORE INTO pildora_link(from_id, to_id, kind) SELECT ?, to_id, kind FROM pildora_link WHERE from_id = ?",
				copy.getId(), id);
		TagDao.invalidateCache();
		return copy;
	}

	public void restaurar(int id) {
		exec("UPDATE pildoras SET eliminada = 0, fecha_eliminacion = NULL WHERE id = ?", id);
		TagDao.invalidateCache();
	}

	public int contarPapelera() {
		String sql = "SELECT COUNT(*) FROM pildoras WHERE eliminada = 1";
		try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			if (rs.next()) return rs.getInt(1);
		} catch (SQLException e) { e.printStackTrace(); }
		return 0;
	}

	public List<TrashItem> listarPapelera() {
		String sql = "SELECT id, titulo, fecha_eliminacion FROM pildoras WHERE eliminada = 1 "
				+ "ORDER BY fecha_eliminacion DESC, id DESC";
		List<TrashItem> out = new ArrayList<>();
		try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			while (rs.next())
				out.add(new TrashItem(rs.getInt("id"), rs.getString("titulo"), rs.getString("fecha_eliminacion")));
		} catch (SQLException e) { e.printStackTrace(); }
		return out;
	}

	public void vaciarPapelera() {
		for (TrashItem t : listarPapelera())
			eliminarDefinitivo(t.id());
	}

	private void exec(String sql, Object... args) {
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Integer n) ps.setInt(i + 1, n);
				else ps.setString(i + 1, (String) args[i]);
			}
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void eliminarDefinitivo(int id) {
		cleanupDanglingRefs(id);
		TagDao.invalidateCache();
		String sql = "DELETE FROM pildoras WHERE id = ?";
		try (PreparedStatement pstmt = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, id);
			pstmt.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void cleanupDanglingRefs(int deletedId) {
		String pattern = "\\[([^\\]]*)]\\(pildora:" + deletedId + "\\)";
		var regex = java.util.regex.Pattern.compile(pattern);
		String findSql = """
			SELECT p.id, p.descripcion
			FROM pildora_link l
			JOIN pildoras p ON p.id = l.from_id
			WHERE l.to_id = ? AND l.kind = 'ref' AND p.protegida = 0
		""";
		String updateSql = "UPDATE pildoras SET descripcion = ? WHERE id = ?";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(findSql)) {
			ps.setInt(1, deletedId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int pid = rs.getInt("id");
					String md = rs.getString("descripcion");
					if (md == null) continue;
					String cleaned = regex.matcher(md).replaceAll("$1");
					if (!cleaned.equals(md)) {
						try (PreparedStatement up = DatabaseHelper.getInstance().getConnection().prepareStatement(updateSql)) {
							up.setString(1, cleaned);
							up.setInt(2, pid);
							up.execute();
						}
					}
				}
			}
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

	public int contarTotal() {
		return countWhere("");
	}

	public int contarProtegidas() {
		return countWhere("AND protegida = 1");
	}

	public int contarFavoritas() {
		return countWhere("AND favorita = 1");
	}

	public int contarOcultas() {
		return countWhere("AND oculta = 1");
	}

	public int contarCreadasDesde(LocalDateTime since) {
		String sql = "SELECT COUNT(*) FROM pildoras WHERE eliminada = 0 AND fecha_creacion >= ?";
		try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
			ps.setString(1, since.format(DATETIME_FMT));
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) return rs.getInt(1);
			}
		} catch (SQLException e) { e.printStackTrace(); }
		return 0;
	}

	private int countWhere(String where) {
		String sql = "SELECT COUNT(*) FROM pildoras WHERE eliminada = 0 " + where;
		try (Statement st = DatabaseHelper.getInstance().getConnection().createStatement();
			 ResultSet rs = st.executeQuery(sql)) {
			if (rs.next()) return rs.getInt(1);
		} catch (SQLException e) { e.printStackTrace(); }
		return 0;
	}

}
