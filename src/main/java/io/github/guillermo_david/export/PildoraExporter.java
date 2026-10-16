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
import io.github.guillermo_david.util.I18n;

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
            throw new IllegalStateException(I18n.get("export.protected.error"));
        return sec.decrypt(p.getDescripcionCipher(), p.getDescripcionIv());
    }

    private static final java.util.regex.Pattern P_PILDORA_MD_LINK =
            java.util.regex.Pattern.compile("\\[([^\\]]*)\\]\\([pP]ildora:\\d+\\)");

    public static void exportMarkdown(Pildora p, TagDao tagDao, Path file) throws Exception {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        String bodyMd = resolveMarkdownBody(p);
        bodyMd = P_PILDORA_MD_LINK.matcher(bodyMd).replaceAll("$1");

        String md = """
                # %s

                _Creada_: %s  
                _Tags_: %s

                ---
                
                %s
                """.formatted(
                p.getTitulo(),
                p.getFechaCreacion() != null ? p.getFechaCreacion().format(DF) : I18n.get("export.no.date"),
                (tags == null || tags.isBlank()) ? "—" : tags,
                bodyMd == null ? "" : bodyMd
        );

        Files.writeString(file, md, StandardCharsets.UTF_8);
    }

    public static void exportHtml(Pildora p, TagDao tagDao, Path file, String extraCss) throws Exception {
        String fullHtml = buildFullHtml(p, tagDao, extraCss, true);
        Files.writeString(file, fullHtml, StandardCharsets.UTF_8);
    }

    public static void exportPdf(Pildora p, TagDao tagDao, Path file, String extraCss) throws Exception {
        String html = buildFullHtml(p, tagDao, extraCss, false);
        html = toXhtml(html);
        try (var os = Files.newOutputStream(file)) {
            var builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
        }
    }

    private static String toXhtml(String html) {
        html = html.replace("<!doctype html>",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        html = html.replace("<html lang=\"es\">",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"es\">");
        html = html.replaceAll("<(meta|br|hr|img|input|link)(\\s[^>]*?)?>",
                "<$1$2 />");
        return html;
    }

    private static String buildFullHtml(Pildora p, TagDao tagDao, String extraCss, boolean includeMathjax) {
        String tags = tagDao.findByPildoraId(p.getId()).stream()
                .map(t -> t.getNombre())
                .collect(Collectors.joining(", "));

        String bodyMd = resolveMarkdownBody(p);
        Node doc = MD_PARSER.parse(bodyMd == null ? "" : bodyMd);
        String bodyHtml = HTML_REND.render(doc);
        bodyHtml = bodyHtml.replaceAll("<a\\s+href\\s*=\\s*\"[pP]ildora:\\d+\"\\s*>(.*?)</a>", "$1");

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
            pre{padding:12px;background:rgba(15,23,42,0.06);border-radius:6px;overflow:auto}
        """;

        String mathjaxBlock = !includeMathjax ? "" : """
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

        String dateStr = p.getFechaCreacion() != null
                ? p.getFechaCreacion().format(DF)
                : I18n.get("export.no.date");
        String chipsHtml = (tags == null || tags.isBlank()) ? "" : tagsToChips(tags);

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"es\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        sb.append("  <title>").append(escape(p.getTitulo())).append("</title>\n");
        sb.append("  <style>\n").append(BASE_WEBVIEW_CSS).append('\n');
        sb.append(pageCss).append('\n');
        if (extraCss != null && !extraCss.isEmpty()) sb.append(extraCss).append('\n');
        sb.append("  </style>\n");
        sb.append(mathjaxBlock);
        sb.append("</head>\n<body class=\"md-body\">\n");
        sb.append("  <h1>").append(escape(p.getTitulo())).append("</h1>\n");
        sb.append("  <div class=\"meta\">\n");
        sb.append("    <div><strong>").append(escape(I18n.get("export.created"))).append(" </strong>").append(escape(dateStr)).append("</div>\n");
        sb.append("    <div class=\"tags\">").append(chipsHtml).append("</div>\n");
        sb.append("  </div>\n");
        sb.append(bodyHtml).append('\n');
        sb.append("</body>\n</html>");
        return sb.toString();
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
