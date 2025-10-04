package io.github.guillermo_david.export;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.security.SecurityService;

public final class PildoraExporter {

	// css web común para markdown (tablas, toc, task-list, code, etc.)
	private static final String BASE_WEBVIEW_CSS = readResource("/css/base-webview.css");
	
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    
    
 // lee un recurso del classpath a String
    private static String readResource(String path) {
        try (var is = PildoraExporter.class.getResourceAsStream(path)) {
            return (is == null) ? "" : new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** Misma config que usas en la app (tablas, task list, TOC, etc.) */
    private static final MutableDataSet MD_OPTS = new MutableDataSet()
            .set(Parser.EXTENSIONS, java.util.Arrays.asList(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    TaskListExtension.create(),
                    FootnoteExtension.create(),
                    TocExtension.create(),
                    AutolinkExtension.create(),
                    AnchorLinkExtension.create(),
                    EmojiExtension.create(),
                    AttributesExtension.create()
            ))
            .set(HtmlRenderer.SOFT_BREAK, "<br/>")
            .set(AnchorLinkExtension.ANCHORLINKS_SET_ID, true)
            .set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false);

    private static final Parser      MD_PARSER  = Parser.builder(MD_OPTS).build();
    private static final HtmlRenderer HTML_REND = HtmlRenderer.builder(MD_OPTS).build();

    private PildoraExporter() {}

    /** Obtiene el cuerpo en Markdown (descifrando si es protegida). */
    private static String resolveMarkdownBody(Pildora p) {
        if (p == null) return "";
        if (!p.isProtegida()) return p.getDescripcion() == null ? "" : p.getDescripcion();

        // Protegida -> exige estar desbloqueado y descifra
        SecurityService sec = SecurityService.getInstance();
        if (!sec.isUnlocked())
            throw new IllegalStateException("Contenido protegido: desbloquea con PIN antes de exportar.");
        return sec.decrypt(p.getDescripcionCipher(), p.getDescripcionIv());
    }

    public static void exportMarkdown(Pildora p, TagDao tagDao, Path file) throws Exception {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        String bodyMd = resolveMarkdownBody(p);

        String md = """
                # %s

                _Creada_: %s  
                _Tags_: %s

                ---
                
                %s
                """.formatted(
                p.getTitulo(),
                p.getFechaCreacion() != null ? p.getFechaCreacion().format(DF) : "sin fecha",
                (tags == null || tags.isBlank()) ? "—" : tags,
                bodyMd == null ? "" : bodyMd
        );

        Files.writeString(file, md, StandardCharsets.UTF_8);
    }

    public static void exportHtml(Pildora p, TagDao tagDao, Path file, String extraCss) throws Exception {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        String bodyMd = resolveMarkdownBody(p);
        Node doc = MD_PARSER.parse(bodyMd == null ? "" : bodyMd);
        String bodyHtml = HTML_REND.render(doc);

        // CSS de “página” (layout/tipografía/chips). Lo de markdown vive en base-webview.css
        String pageCss = """
            body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Helvetica,Arial,sans-serif;
                 line-height:1.6;padding:24px;max-width:900px;margin:auto;}
            h1{margin-top:0}
            .meta{color:#667085;font-size:0.95rem;margin-bottom:16px}
            .tags{margin-top:4px}
            .chip{display:inline-block;padding:.15rem .5rem;border-radius:999px;font-size:.80rem;
                  margin-right:.35rem;margin-bottom:.35rem;background:#eef2ff;color:#3730a3}
            pre,code{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,
                     "Liberation Mono","Courier New",monospace}
            pre{padding:12px;background:#0f172a0f;border-radius:6px;overflow:auto}
        """;

        String mathjax = """
          <script>
            window.MathJax = {
              tex: {
                inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
                displayMath: [['$$','$$'], ['\\\\[','\\\\]']]
              },
              options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },
              svg: { fontCache: 'global' }
            };
          </script>
          <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
        """;

        String page = """
          <!doctype html>
          <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>%s</title>
              <style>
                %s   /* base-webview.css: reglas markdown (.md-body, tablas, toc, task list, etc.) */
                %s   /* CSS de página (tipografía/layout/chips) */
                %s   /* extraCss para colorear chips por tag (opcional) */
              </style>
              %s    
            </head>
            <body class="md-body">
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
            BASE_WEBVIEW_CSS,
            pageCss,
            (extraCss == null ? "" : extraCss),
            mathjax,
            escape(p.getTitulo()),
            p.getFechaCreacion() != null ? p.getFechaCreacion().format(DF) : "sin fecha",
            (tags == null || tags.isBlank()) ? "" : tagsToChips(tags),
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
