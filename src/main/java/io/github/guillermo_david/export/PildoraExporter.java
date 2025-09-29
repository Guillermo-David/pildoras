package io.github.guillermo_david.export;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.model.Pildora;

public final class PildoraExporter {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Parser      MD_PARSER  = Parser.builder().build();
    private static final HtmlRenderer HTML_REND = HtmlRenderer.builder().build();

    private PildoraExporter() {}

    public static void exportMarkdown(Pildora p, TagDao tagDao, Path file) throws Exception {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        String md = """
                # %s

                _Creada_: %s  
                _Tags_: %s

                ---

                %s
                """.formatted(
                p.getTitulo(),
                p.getFechaCreacion() != null ? p.getFechaCreacion().format(DF) : "sin fecha",
                tags.isBlank() ? "—" : tags,
                p.getDescripcion()
        );

        Files.writeString(file, md, StandardCharsets.UTF_8);
    }

    public static void exportHtml(Pildora p, TagDao tagDao, Path file, String extraCss /* puede ser null */) throws Exception {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        // Renderiza el cuerpo (que ya es Markdown) a HTML
        Node doc = MD_PARSER.parse(p.getDescripcion());
        String bodyHtml = HTML_REND.render(doc);

        String page = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                <style>
                  body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Helvetica,Arial,sans-serif;line-height:1.6;padding:24px;max-width:900px;margin:auto;}
                  h1{margin-top:0}
                  .meta{color:#667085;font-size:0.95rem;margin-bottom:16px}
                  .tags{margin-top:4px}
                  .chip{display:inline-block;padding:.15rem .5rem;border-radius:999px;font-size:.80rem;margin-right:.35rem;margin-bottom:.35rem;background:#eef2ff;color:#3730a3}
                  %s
                  pre,code{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace}
                  pre{padding:12px;background:#0f172a0f;border-radius:6px;overflow:auto}
                </style>
              </head>
              <body>
                <h1>%s</h1>
                <div class="meta">
                  <div><strong>Creada:</strong> %s</div>
                  <div class="tags">%s</div>
                </div>
                %s
              </body>
            </html>
            """.formatted(
                escape(p.getTitulo()),
                extraCss == null ? "" : extraCss,
                escape(p.getTitulo()),
                p.getFechaCreacion() != null ? p.getFechaCreacion().format(DF) : "sin fecha",
                tags.isBlank() ? "" : tagsToChips(tags),
                bodyHtml
        );

        Files.writeString(file, page, StandardCharsets.UTF_8);
    }

    private static String tagsToChips(String csv) {
        StringBuilder sb = new StringBuilder();
        for (String t : csv.split(",")) {
            String tag = t.trim();
            if (tag.isEmpty()) continue;
            sb.append("<span class=\"chip\" data-tag=\"")
              .append(escape(tag))
              .append("\">")
              .append(escape(tag))
              .append("</span>");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s
            .replace("&","&amp;")
            .replace("<","&lt;")
            .replace(">","&gt;");
    }
}
