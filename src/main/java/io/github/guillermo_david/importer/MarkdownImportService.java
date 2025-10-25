package io.github.guillermo_david.importer;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.dao.PildoraLinkDao;
import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Importa un archivo Markdown:
 *  - Línea 1: "# Título"
 *  - Línea con tags: "_Tags_: a, b, c" (case-insensitive; admite "*Tags*:" o "Tags:" también)
 *  - Contenido: desde la línea 8 en adelante (1-based)
 *  - Ignora fechas del fichero. No protege la píldora (protegida=false).
 */
public class MarkdownImportService {

    public static record Result(int pildoraId, String titulo, List<String> tags) {}

    private static final Pattern P_TAGS_LINE = Pattern.compile(
            "^\\s*(?:[_*]?tags[_*]?|tags)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BOM = Pattern.compile("^\uFEFF"); // quita BOM si viene

    private final PildoraDao pildoraDao = new PildoraDao();
    private final TagDao tagDao = new TagDao();
    private final PildoraTagDao pildoraTagDao = new PildoraTagDao();
    private final PildoraLinkDao pildoraLinkDao = new PildoraLinkDao();

    public Result importFile(File mdFile) throws Exception {
        if (mdFile == null) throw new IllegalArgumentException("Fichero .md nulo");
        if (!mdFile.isFile()) throw new IllegalArgumentException("No es un fichero válido: " + mdFile);

        List<String> lines = Files.readAllLines(mdFile.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalArgumentException("El markdown está vacío");

        // --- Título (línea 1)
        String first = stripBom(lines.get(0));
        if (!first.startsWith("# ")) {
            throw new IllegalArgumentException("La primera línea debe empezar por \"# \": " + first);
        }
        String titulo = first.substring(2).trim();
        if (titulo.isBlank()) throw new IllegalArgumentException("El título (línea 1) está vacío");

        // --- Tags (buscar línea con _Tags_: …)
        List<String> tags = new ArrayList<>();
        for (String raw : lines) {
            var m = P_TAGS_LINE.matcher(raw);
            if (m.find()) {
                String tail = m.group(1);
                for (String part : tail.split(",")) {
                    String t = normalizeTag(part);
                    if (!t.isBlank() && !tags.contains(t)) tags.add(t);
                }
                break; // primera coincidencia
            }
        }

        // --- Contenido: desde la línea 8 (1-based) => índice 7 (0-based)
        String cuerpo = "";
        if (lines.size() >= 8) {
            cuerpo = String.join("\n", lines.subList(7, lines.size())).strip();
        }

        // --- Persistencia atómica (misma conexión)
        Connection cx = DatabaseHelper.getInstance().getConnection();
        boolean oldAuto = cx.getAutoCommit();
        cx.setAutoCommit(false);
        try {
            // NO protegida: en claro
            Pildora p = new Pildora(titulo, cuerpo, /*protegida*/ false, /*cipher*/ null, /*iv*/ null);
            pildoraDao.insertar(p); // asigna id

            // Tags
            pildoraTagDao.removeAllTagsFromPildora(p.getId());
            for (String t : tags) {
                Tag tag = tagDao.findOrCreate(t);
                if (tag != null) {
                    pildoraTagDao.addTagToPildora(p.getId(), tag.getId());
                }
            }

            // Enlaces internos "pildora:<id>" detectados en el cuerpo
            try {
                var ids = extractLinkedIds(cuerpo);
                pildoraLinkDao.replaceRefs(p.getId(), ids);
            } catch (Exception ignore) {
                // no bloquea la importación si falla la sincronización de referencias
            }

            cx.commit();
            return new Result(p.getId(), titulo, tags);
        } catch (Exception ex) {
            try { cx.rollback(); } catch (Exception ignore) {}
            throw ex;
        } finally {
            try { cx.setAutoCommit(oldAuto); } catch (Exception ignore) {}
        }
    }

    // --- Helpers ---

    private static String stripBom(String s) {
        return P_BOM.matcher(s == null ? "" : s).replaceFirst("");
    }

    private static String normalizeTag(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static final Pattern P_PILDORA_ID = Pattern.compile("\\b[pP]ildora:(\\d+)");

    private static java.util.Set<Integer> extractLinkedIds(String md) {
        var ids = new java.util.HashSet<Integer>();
        if (md == null || md.isBlank()) return ids;
        var m = P_PILDORA_ID.matcher(md);
        while (m.find()) {
            try {
                int id = Integer.parseInt(m.group(1));
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignore) {}
        }
        return ids;
    }
}
