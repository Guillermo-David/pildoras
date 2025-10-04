package io.github.guillermo_david.markdown;

import java.util.Arrays;

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

public final class MarkdownEngine {
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownEngine() {
        MutableDataSet opts = new MutableDataSet()
            .set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                FootnoteExtension.create(),
                TocExtension.create(),
                AutolinkExtension.create(),
                AnchorLinkExtension.create(),
                EmojiExtension.create(),
                AttributesExtension.create(),
                TypographicExtension.create()
//                MathLatexExtension.create()
            ))
            // TOC config
            .set(TocExtension.LEVELS, 255)
            .set(TocExtension.TITLE, "Contenido")
            .set(TocExtension.DIV_CLASS, "toc")
            // Rendering tweaks
            .set(HtmlRenderer.SOFT_BREAK, "<br/>");
        
        // Ejemplo de opciones opcionales:
//        opts.set(AnchorLinkExtension.ANCHORLINKS_SET_ID, true);
//        opts.set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false);
//        opts.set(AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "h-anchor");


        this.parser = Parser.builder(opts).build();
        this.renderer = HtmlRenderer.builder(opts).build();
    }

    public String toHtml(String markdown) {
        if (markdown == null) markdown = "";
        Node doc = parser.parse(markdown);
        return renderer.render(doc);
    }
}
