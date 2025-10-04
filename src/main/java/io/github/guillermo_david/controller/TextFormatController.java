package io.github.guillermo_david.controller;

import io.github.guillermo_david.javafx.Dialogs;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class TextFormatController {

	TextArea txtDescripcion;
	VBox root;
	public TextFormatController(VBox root, TextArea txtDescripcion) {
		this.txtDescripcion = txtDescripcion;
		this.root = root;
	}
	// ==== Utils básicos de selección/edición ====

	// Devuelve selección actual (o vacío)
	private String sel() {
	    return txtDescripcion.getSelectedText();
	}
	private int selStart() { return txtDescripcion.getSelection().getStart(); }
	private int selEnd()   { return txtDescripcion.getSelection().getEnd(); }

	// Reemplaza la selección por texto y recoloca el cursor al final de lo insertado
	private void replaceSel(String text) {
	    int s = selStart();
	    txtDescripcion.replaceText(s, selEnd(), text);
	    txtDescripcion.positionCaret(s + text.length());
	}

	// Inserta en la posición del caret si no hay selección
	private void insertAtCaret(String text) {
	    int pos = txtDescripcion.getCaretPosition();
	    txtDescripcion.insertText(pos, text);
	    txtDescripcion.positionCaret(pos + text.length());
	}

	// Obtiene inicio y fin de línea que abarca la selección
	private int lineStart(int pos) {
	    String t = txtDescripcion.getText();
	    int i = t.lastIndexOf('\n', Math.max(0,pos-1));
	    return i == -1 ? 0 : i+1;
	}
	private int lineEnd(int pos) {
	    String t = txtDescripcion.getText();
	    int i = t.indexOf('\n', pos);
	    return i == -1 ? t.length() : i;
	}

	// Envuelve/alternar inline: si la selección ya está envuelta con prefix/suffix -> quita; si no -> envuelve.
	// Si no hay selección, inserta plantilla y coloca el cursor dentro.
	private void toggleWrapInline(String prefix, String suffix, String placeholder) {
	    int s = selStart(), e = selEnd();
	    String full = txtDescripcion.getText();
	    if (s == e) {
	        String insert = prefix + placeholder + suffix;
	        insertAtCaret(insert);
	        // coloca el cursor entre los envoltorios
	        txtDescripcion.positionCaret(txtDescripcion.getCaretPosition() - suffix.length());
	        return;
	    }
	    String selected = full.substring(s, e);
	    boolean already =
	            selected.startsWith(prefix) && selected.endsWith(suffix);
	    if (already) {
	        String inner = selected.substring(prefix.length(), selected.length() - suffix.length());
	        replaceSel(inner);
	    } else {
	        replaceSel(prefix + selected + suffix);
	    }
	}

	// Aplica/borra prefijo por línea (toggle). Si TODAS las líneas ya lo tienen, lo quita; si no, lo pone a todas.
	private void toggleLinePrefix(String addPrefix, String regexExisting) {
	    int s0 = lineStart(selStart());
	    int e0 = lineEnd(selEnd());
	    String text = txtDescripcion.getText().substring(s0, e0);
	    String[] lines = text.split("\n", -1);

	    boolean allHave = true;
	    for (String ln : lines) {
	        if (!ln.matches(regexExisting)) { allHave = false; break; }
	    }

	    StringBuilder out = new StringBuilder();
	    if (allHave) {
	        // quitar
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i];
	            out.append(ln.replaceFirst(regexExisting, "")); // quita el prefijo existente
	            if (i < lines.length-1) out.append('\n');
	        }
	    } else {
	        // poner
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i];
	            out.append(addPrefix).append(ln);
	            if (i < lines.length-1) out.append('\n');
	        }
	    }

	    // Sustituir bloque
	    txtDescripcion.replaceText(s0, e0, out.toString());
	    // Selección abarcar el nuevo bloque para continuidad
	    txtDescripcion.selectRange(s0, s0 + out.length());
	}

	// Re-numerar o alternar lista numerada
	private void toggleNumberedList() {
	    int s0 = lineStart(selStart());
	    int e0 = lineEnd(selEnd());
	    String text = txtDescripcion.getText().substring(s0, e0);
	    String[] lines = text.split("\n", -1);

	    boolean allNumbered = true;
	    for (String ln : lines) {
	        if (!ln.matches("\\d+\\.\\s.*") && !ln.isBlank()) { allNumbered = false; break; }
	    }

	    StringBuilder out = new StringBuilder();
	    if (allNumbered) {
	        // quitar números "^\d+\.\s"
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i].replaceFirst("^\\d+\\.\\s", "");
	            out.append(ln);
	            if (i < lines.length-1) out.append('\n');
	        }
	    } else {
	        int n=1;
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i];
	            if (ln.isBlank()) { out.append(ln); }
	            else { out.append(n++).append(". ").append(ln); }
	            if (i < lines.length-1) out.append('\n');
	        }
	    }
	    txtDescripcion.replaceText(s0, e0, out.toString());
	    txtDescripcion.selectRange(s0, s0 + out.length());
	}

	// Alterna lista de tareas: "- [ ] " <-> "- [x] " o añade si no hay
	private void toggleTaskList() {
	    int s0 = lineStart(selStart());
	    int e0 = lineEnd(selEnd());
	    String text = txtDescripcion.getText().substring(s0, e0);
	    String[] lines = text.split("\n", -1);

	    boolean allTasks = true;
	    for (String ln : lines) {
	        if (!ln.matches("(- \\[ \\] |- \\[x\\] ).*") && !ln.isBlank()) { allTasks = false; break; }
	    }

	    StringBuilder out = new StringBuilder();
	    if (allTasks) {
	        // Si todas son tareas, alterna check a unchecked (o viceversa) según la primera
	        boolean firstChecked = lines[0].matches("(- \\[x\\] ).*");
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i];
	            if (ln.matches("(- \\[ \\] |- \\[x\\] ).*")) {
	                if (firstChecked) ln = ln.replaceFirst("^- \\[x\\] ", "- [ ] ");
	                else ln = ln.replaceFirst("^- \\[ \\] ", "- [x] ");
	            } else if (!ln.isBlank()) {
	                ln = "- [ ] " + ln;
	            }
	            out.append(ln);
	            if (i < lines.length-1) out.append('\n');
	        }
	    } else {
	        // Convierte selección a tareas
	        for (int i=0;i<lines.length;i++) {
	            String ln = lines[i];
	            if (!ln.isBlank() && !ln.matches("(- \\[ \\] |- \\[x\\] ).*")) {
	                ln = "- [ ] " + ln.replaceFirst("^(-\\s|\\d+\\.\\s)?", ""); // limpia prefijos de lista
	            }
	            out.append(ln);
	            if (i < lines.length-1) out.append('\n');
	        }
	    }
	    txtDescripcion.replaceText(s0, e0, out.toString());
	    txtDescripcion.selectRange(s0, s0 + out.length());
	}

	// Inserta en nueva línea (respetando salto)
	private void insertOnNewLine(String block) {
	    int pos = txtDescripcion.getCaretPosition();
	    String t = txtDescripcion.getText();
	    // si no estamos al inicio de línea, baja una línea
	    if (pos > 0 && t.charAt(pos-1) != '\n') {
	        insertAtCaret("\n");
	    }
	    insertAtCaret(block);
	}

	// ==== Handlers básicos (B, I, ~~ , `) ====
	public void mdBold()       { toggleWrapInline("**", "**", "negrita"); }
	public void mdItalic()     { toggleWrapInline("*", "*", "cursiva"); }
	public void mdStrike()     { toggleWrapInline("~~", "~~", "tachado"); }
	public void mdCodeInline() { toggleWrapInline("`", "`", "codigo"); }

	// ==== Encabezados ====
	public void applyHeading(int level) {
	    int s0 = lineStart(selStart());
	    int e0 = lineEnd(selEnd());
	    String line = txtDescripcion.getText().substring(s0, e0);
	    // Limpia encabezado previo
	    line = line.replaceFirst("^(#{1,6})\\s+", "");
	    if (level > 0) {
	        String hashes = "#".repeat(level);
	        line = hashes + " " + (line.isBlank()? "Título" : line);
	    }
	    txtDescripcion.replaceText(s0, e0, line);
	    txtDescripcion.positionCaret(Math.min(s0 + line.length(), txtDescripcion.getText().length()));
	}
	public void mdH1(){ applyHeading(1); }
	public void mdH2(){ applyHeading(2); }
	public void mdH3(){ applyHeading(3); }
	public void mdClearHeading(){ applyHeading(0); }

	// ==== Listas / Bloques ====
	public void mdBulletedList()  { toggleLinePrefix("- ", "^-\\s+"); }
	public void mdNumberedList()  { toggleNumberedList(); }
	public void mdTaskList()      { toggleTaskList(); }
	public void mdBlockquote()    { toggleLinePrefix("> ", "^>\\s+"); }

	public void mdCodeBlock() {
	    String s = sel();
	    if (s.contains("\n")) {
	        replaceSel("```\n" + s + "\n```");
	    } else {
	        insertOnNewLine("```\n\n```");
	        // coloca caret entre las líneas
	        txtDescripcion.positionCaret(txtDescripcion.getCaretPosition() - 4);
	    }
	}

	public void mdHorizontalRule() { insertOnNewLine("\n---\n"); }

	public void mdTable2x2() {
	    String tbl = """
	            | Col 1 | Col 2 |
	            | ----- | ----- |
	            |       |       |
	            |       |       |
	            """;
	    insertOnNewLine(tbl);
	}

	// ==== Insertar: enlace/imagen (diálogos simples) ====
	public void mdInsertLinkDialog() {
	    TextInputDialogEx td = new TextInputDialogEx("Texto del enlace", "URL (https://…)");
	    var res = td.showAndWait();
	    if (res == null) return;
	    String text = res[0].isBlank()? "enlace" : res[0];
	    String url  = res[1].isBlank()? "https://" : res[1];
	    replaceSel("[" + text + "](" + url + ")");
	}

	public void mdInsertImageDialog() {
	    TextInputDialogEx td = new TextInputDialogEx("Texto alternativo", "URL de la imagen (https://…)");
	    var res = td.showAndWait();
	    if (res == null) return;
	    String alt = res[0].isBlank()? "imagen" : res[0];
	    String url = res[1].isBlank()? "https://" : res[1];
	    insertOnNewLine("![" + alt + "](" + url + ")\n");
	}

	// ==== Avanzado (requiere soporte del renderer para verse perfecto) ====
	public void mdFootnote() {
	    // Busca el próximo índice disponible
	    String all = txtDescripcion.getText();
	    int idx = 1;
	    while (all.contains("[^" + idx + "]") || all.contains("[^" + idx + "]:")) idx++;

	    // Inserta referencia en texto
	    insertAtCaret("[^" + idx + "]");
	    // Añade definición al final del documento
	    txtDescripcion.appendText("\n\n[^" + idx + "]: Detalle de la nota\n");
	}

	public void mdInsertTOC() {
	    insertOnNewLine("[TOC]\n");
	}

	public void mdMathInline() {
	    toggleWrapInline("$", "$", "E=mc^2");
	}

	public void mdMathBlock() {
	    insertOnNewLine("$$\n\n$$\n");
	    txtDescripcion.positionCaret(txtDescripcion.getCaretPosition() - 4);
	}
	
	// ==== Math: helpers de inserción ====
	private void insertMathInline(String latex) {
	    // Si hay selección, la metemos dentro del $...$ si procede
	    String s = sel();
	    if (s != null && !s.isBlank() && latex.contains("%SEL%")) {
	        latex = latex.replace("%SEL%", s);
	        replaceSel("$" + latex + "$");
	    } else if (s != null && !s.isBlank() && latex.equals("%SEL%")) {
	        replaceSel("$" + s + "$");
	    } else {
	        // placeholder si no hay selección
	        toggleWrapInline("$", "$", latex.replace("%SEL%", "\\;"));
	    }
	}

	private void insertMathBlock(String latex) {
	    insertOnNewLine("$$\n" + latex + "\n$$\n");
	    // Coloca el caret al final del bloque (sencillo y suficiente)
	    txtDescripcion.positionCaret(Math.min(txtDescripcion.getText().length(), txtDescripcion.getCaretPosition()));
	}

	// Reemplaza selección por un template e intenta colocar el caret dentro del primer "{}" si existe.
	private void replaceSelTemplate(String template) {
	    int s = selStart();
	    txtDescripcion.replaceText(s, selEnd(), template);
	    int idx = template.indexOf("{}");
	    if (idx >= 0) {
	        txtDescripcion.positionCaret(s + idx + 1);
	    } else {
	        txtDescripcion.positionCaret(s + template.length());
	    }
	}

	// ==== Math: acciones de plantilla "inteligentes" ====
	public void mdMathFrac() {
	    String s = sel();
	    if (s != null && !s.isBlank()) {
	        replaceSelTemplate("$\\frac{" + s + "}{}$"); // caret dentro del segundo {}
	    } else {
	        replaceSelTemplate("$\\frac{}{}$");          // caret dentro del primero {}
	    }
	}

	public void mdMathSqrt() {
	    String s = sel();
	    if (s != null && !s.isBlank()) {
	        replaceSelTemplate("$\\sqrt{" + s + "}$");
	    } else {
	        replaceSelTemplate("$\\sqrt{}$");
	    }
	}

	public void mdMathNSqrt() {
	    // raíz con índice: \sqrt[n]{…}
	    replaceSelTemplate("$\\sqrt[n]{}$");
	}

	public void mdMathSup() { // superíndice
	    String s = sel();
	    if (s != null && !s.isBlank()) {
	        replaceSelTemplate("$" + s + "^{}}$");
	    } else {
	        replaceSelTemplate("$x^{}$");
	    }
	}

	public void mdMathSub() { // subíndice
	    String s = sel();
	    if (s != null && !s.isBlank()) {
	        replaceSelTemplate("$" + s + "_{}$");
	    } else {
	        replaceSelTemplate("$x_{}$");
	    }
	}
	
	public void mdMathVec() { replaceSelTemplate("$\\vec{}$"); }
	public void mdMathBar() { replaceSelTemplate("$\\bar{}$"); }
	public void mdMathHat() { replaceSelTemplate("$\\hat{}$"); }

	public void mdMathLim() { insertMathInline("\\lim_{x\\to 0}"); }
	// === Sumatorio ===
	public void mdMathSum() {
	    // Literal exacto requerido en el editor:
	    //   $\sum_\{i=1}^{n\}{}$
	    final String snippet = "$\\sum_\\{i=1}^{n\\}{}$";

	    int s = selStart(), e = selEnd();
	    String selected = sel();

	    if (s != e && !selected.isBlank()) {
	        // Con selección: coloca la selección como cuerpo
	        String out = "$\\sum_\\{i=1}^{n\\}{" + selected + "}$";
	        txtDescripcion.replaceText(s, e, out);
	        txtDescripcion.positionCaret(s + out.length());
	    } else {
	        // Sin selección: inserta la estructura y deja el cursor entre {}
	        int pos = txtDescripcion.getCaretPosition();
	        txtDescripcion.insertText(pos, snippet);
	        // Posición entre las llaves de "{}" antes del '$' final:
	        txtDescripcion.positionCaret(pos + snippet.length() - 2);
	    }
	}

	public void mdMathProd() {
	    final String snippet = "$\\prod_\\{i=1}^{n\\}$";
	    int pos = txtDescripcion.getCaretPosition();
	    txtDescripcion.insertText(pos, snippet);
	    txtDescripcion.positionCaret(pos + snippet.length());
	}

	public void mdMathInt() {
	    final String snippet = "$\\int_\\{a}^{b\\}$";
	    int pos = txtDescripcion.getCaretPosition();
	    txtDescripcion.insertText(pos, snippet);
	    txtDescripcion.positionCaret(pos + snippet.length());
	}

	// Delimitadores con \left \right
	public void mdMathDelimsParen() { wrapWithLeftRight("(", ")"); }
	public void mdMathDelimsBrack() { wrapWithLeftRight("[", "]"); }
	
	private void wrapWithLeftRight(String open, String close) {
	    int s = selStart(), e = selEnd();
	    String full = txtDescripcion.getText();
	    String selected = full.substring(s, e);

	    // Si no hay selección, deja un espacio para escribir dentro
	    String inner = selected.isBlank() ? " " : selected;

	    // Ojo: aquí queremos que el editor contenga doble barra para { y }
	    String out = "$\\left" + open + inner + "\\right" + close + "$";

	    txtDescripcion.replaceText(s, e, out);

	    // Coloca el caret dentro si no había selección
	    if (s == e) {
	        int caret = s + ("$\\left" + open + " ").length();
	        txtDescripcion.positionCaret(caret);
	    }
	}

	// Llamador específico para llaves
	public void mdMathDelimsBrace() {
	    // Inserta literalmente: $\left\\{ … \right\\}$ en el editor
	    wrapWithLeftRight("\\\\{", "\\\\}");
	}

	// Inserción genérica desde menú
	private void insertMathFromMenu(String tex, boolean block) {
	    if (block) insertMathBlock(tex);
	    else       insertMathInline(tex);
	}

	// ==== Diálogo simple de dos campos (reutilizado para enlace/imagen) ====
	private final class TextInputDialogEx {
	    private final Dialog<String[]> dlg = new Dialog<>();
	    private final TextField tf1 = new TextField();
	    private final TextField tf2 = new TextField();
	    TextInputDialogEx(String prompt1, String prompt2) {
	        dlg.setTitle("Insertar");
	        dlg.setHeaderText(null);
	        tf1.setPromptText(prompt1);
	        tf2.setPromptText(prompt2);
	        var content = new VBox(8, new Label(prompt1 + ":"), tf1, new Label(prompt2 + ":"), tf2);
	        content.setPadding(new Insets(8));
	        dlg.getDialogPane().setContent(content);
	        var ok = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
	        var cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
	        dlg.getDialogPane().getButtonTypes().setAll(ok, cancel);
	        // Tema
	        if (root != null) Dialogs.decorate(dlg, root);
	        Platform.runLater(tf1::requestFocus);
	        dlg.setResultConverter(bt -> (bt == ok) ? new String[]{ tf1.getText().trim(), tf2.getText().trim() } : null);
	    }
	    String[] showAndWait() { return dlg.showAndWait().orElse(null); }
	}

	// ==== Indentación/Desindentación con Tab en listas ====
	public void initListIndentShortcuts() {
	    txtDescripcion.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
	        if (e.getCode() == KeyCode.TAB) {
	            e.consume();
	            int s0 = lineStart(selStart());
	            int e0 = lineEnd(selEnd());
	            String text = txtDescripcion.getText().substring(s0, e0);
	            String[] lines = text.split("\n", -1);
	            StringBuilder out = new StringBuilder();
	            boolean shift = e.isShiftDown();
	            for (int i=0;i<lines.length;i++) {
	                String ln = lines[i];
	                if (shift) {
	                    // desindenta si empieza con 4 espacios
	                    if (ln.startsWith("    ")) ln = ln.substring(4);
	                } else {
	                    // indenta 4 espacios
	                    ln = "    " + ln;
	                }
	                out.append(ln);
	                if (i < lines.length-1) out.append('\n');
	            }
	            txtDescripcion.replaceText(s0, e0, out.toString());
	            txtDescripcion.selectRange(s0, s0 + out.length());
	        }
	    });
	}
	
	public void buildMathMenu(javafx.scene.control.MenuButton mb) {
	    mb.getItems().clear();

	    // --- Fracciones / raíces / super-sub / formatos ---
	    var mFrac = new javafx.scene.control.Menu("Fracciones/raíces");
	    mFrac.getItems().addAll(
	        item("Fracción  \\frac{…}{…}", () -> mdMathFrac()),
	        item("Raíz √     \\sqrt{…}",   () -> mdMathSqrt()),
	        item("Raíz n     \\sqrt[n]{…}",() -> mdMathNSqrt()),
	        item("Superíndice  ^{}",       () -> mdMathSup()),
	        item("Subíndice   _{}",        () -> mdMathSub())
	    );

	    var mCal = new javafx.scene.control.Menu("Cálculo");
	    mCal.getItems().addAll(
	        item("Límite      \\lim_{x\\to 0}", () -> mdMathLim()),
	        item("Sumatorio   \\sum_\\{i=1}^{n\\}{}$", () -> mdMathSum()), //$\sum_\{i=1}^{n\}{}$
	        item("Producto    \\prod_\\{i=1}^{n\\}",() -> mdMathProd()),
	        item("Integral    \\int_{a\\}^{b\\}",   () -> mdMathInt())
	    );

	    var mGreek = new javafx.scene.control.Menu("Griegas");
	    // Un subconjunto útil y compacto (añade más si quieres)
	    mGreek.getItems().addAll(
	        insertInline("\\alpha", "α"),
	        insertInline("\\beta",  "β"),
	        insertInline("\\gamma", "γ"),
	        insertInline("\\delta", "δ"),
	        insertInline("\\theta", "θ"),
	        insertInline("\\lambda","λ"),
	        insertInline("\\mu",    "μ"),
	        insertInline("\\pi",    "π"),
	        insertInline("\\sigma", "σ"),
	        insertInline("\\phi",   "φ"),
	        insertInline("\\omega", "ω"),
	        new javafx.scene.control.SeparatorMenuItem(),
	        insertInline("\\Gamma", "Γ"),
	        insertInline("\\Delta", "Δ"),
	        insertInline("\\Theta", "Θ"),
	        insertInline("\\Lambda","Λ"),
	        insertInline("\\Pi",    "Π"),
	        insertInline("\\Sigma", "Σ"),
	        insertInline("\\Phi",   "Φ"),
	        insertInline("\\Omega", "Ω")
	    );

	    var mOps = new javafx.scene.control.Menu("Operadores");
	    mOps.getItems().addAll(
	        insertInline("\\times", "×"),
	        insertInline("\\cdot",  "·"),
	        insertInline("\\pm",    "±"),
	        insertInline("\\mp",    "∓"),
	        insertInline("\\circ",  "∘"),
	        insertInline("\\star",  "⋆")
	    );

	    var mRel = new javafx.scene.control.Menu("Relaciones");
	    mRel.getItems().addAll(
	        insertInline("=",        "="),
	        insertInline("\\neq",    "≠"),
	        insertInline("\\le",     "≤"),
	        insertInline("\\ge",     "≥"),
	        insertInline("\\approx", "≈"),
	        insertInline("\\equiv",  "≡"),
	        insertInline("\\propto", "∝")
	    );

	    var mSet = new javafx.scene.control.Menu("Conjuntos/Lógica");
	    mSet.getItems().addAll(
	        insertInline("\\in",  "∈"),
	        insertInline("\\notin","∉"),
	        insertInline("\\subset", "⊂"),
	        insertInline("\\subseteq","⊆"),
	        insertInline("\\cup",  "∪"),
	        insertInline("\\cap",  "∩"),
	        insertInline("\\emptyset","∅"),
	        insertInline("\\mathbb{R\\}","ℝ"),
	        insertInline("\\mathbb{Z\\}","ℤ"),
	        insertInline("\\mathbb{Q\\}","ℚ"),
	        insertInline("\\mathbb{N\\}","ℕ"),
	        new javafx.scene.control.SeparatorMenuItem(),
	        insertInline("\\land", "∧"),
	        insertInline("\\lor",  "∨"),
	        insertInline("\\lnot", "¬"),
	        insertInline("\\Rightarrow","⇒"),
	        insertInline("\\Leftrightarrow","⇔")
	    );

	    var mArr = new javafx.scene.control.Menu("Flechas");
	    mArr.getItems().addAll(
	        insertInline("\\to",          "→"),
	        insertInline("\\gets",        "←"),
	        insertInline("\\leftrightarrow","↔"),
	        insertInline("\\Rightarrow",  "⇒"),
	        insertInline("\\Leftarrow",   "⇐"),
	        insertInline("\\Longrightarrow","⟹")
	    );

	    var mFmt = new javafx.scene.control.Menu("Formato vector/acento");
	    mFmt.getItems().addAll(
	        item("Vector  \\vec{}", () -> mdMathVec()),
	        item("Barra   \\bar{}", () -> mdMathBar()),
	        item("Sombrero\\hat{}", () -> mdMathHat())
	    );

	    var mDelim = new javafx.scene.control.Menu("Delimitadores");
	    mDelim.getItems().addAll(
	        item("\\left( … \\right)", () -> mdMathDelimsParen()),
	        item("\\left[ … \\right]", () -> mdMathDelimsBrack()),
	        item("$\\left\\{\\right\\}$", () -> mdMathDelimsBrace())
	    );

	    // Acciones rápidas de bloque
	    var mBlock = new javafx.scene.control.Menu("Bloques");
	    mBlock.getItems().addAll(
	        insertBlock("E=mc^2", "Insertar $$…$$ con E=mc^2"),
	        insertBlock("\\int_0^{\\pi} \\sin x\\,dx", "Insertar $$…$$ con integral")
	    );

	    mb.getItems().addAll(mFrac, mCal, mGreek, mOps, mRel, mSet, mArr, mFmt, mDelim, new javafx.scene.control.SeparatorMenuItem(), mBlock);
	}

	// ---- pequeños factories para no repetir código ----
	private javafx.scene.control.MenuItem item(String label, Runnable action) {
	    var mi = new javafx.scene.control.MenuItem(label);
	    mi.setOnAction(e -> action.run());
	    return mi;
	}
	private javafx.scene.control.MenuItem insertInline(String tex, String labelShown) {
	    var mi = new javafx.scene.control.MenuItem(labelShown + "   " + tex);
	    mi.setOnAction(e -> insertMathFromMenu(tex, false));
	    return mi;
	}
	private javafx.scene.control.MenuItem insertBlock(String tex, String label) {
	    var mi = new javafx.scene.control.MenuItem(label);
	    mi.setOnAction(e -> insertMathFromMenu(tex, true));
	    return mi;
	}


}
