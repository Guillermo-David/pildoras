package io.github.guillermo_david.controller;

import java.util.function.Consumer;

//import org.commonmark.node.Node;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.util.I18n;
import io.github.guillermo_david.dao.PildoraLinkDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.export.PildoraExporter;
import io.github.guillermo_david.javafx.ColorUtil;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.markdown.MarkdownEngine;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import netscape.javascript.JSObject;

public class DetallePildoraController {

	@FXML private VBox root;
	@FXML private VBox linksSection;
	@FXML private Label lblTitulo, lblFecha;
	@FXML private FlowPane tagsBox;
	@FXML private WebView webContenido;
	@FXML private Button btnVolver, btnEditar, btnBorrar;
	@FXML private MenuButton btnExportMenu;
	@FXML private FlowPane linksInBox;
	@FXML private Label lblLinksIn;
	@FXML private Button btnShowMoreLinks;

	private final SecurityService security = SecurityService.getInstance();
	private JsBridge jsBridge;

	public static class JsBridge {
		private final DetallePildoraController ctrl;
		JsBridge(DetallePildoraController ctrl) { this.ctrl = ctrl; }
		public void openPildora(Object num) {
			int id;
			if (num instanceof Number n) id = n.intValue();
			else {
				try { id = Integer.parseInt(String.valueOf(num)); } catch (Exception ex) { return; }
			}
			Platform.runLater(() -> ctrl.openPildoraFromJs(id));
		}
	}

	private Runnable onClose;
	private Consumer<Pildora> onEdit;
	private Consumer<Pildora> onDelete;
	private Consumer<String> onTagClick;
	private Pildora currentPildora;

	private EventHandler<KeyEvent> deleteFilter;
	private EventHandler<KeyEvent> escFilter;
	private EventHandler<KeyEvent> searchFilter;
	private KeyCombination kcEditar = new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN);

	private javafx.scene.layout.HBox searchBar;
	private javafx.scene.control.TextField searchField;
	private Label searchStatus;

	private static final MarkdownEngine MD = new MarkdownEngine();
	private final TagDao tagDao = new TagDao();

	private boolean shortcutsInstalados = false;
	private boolean deletingNow = false;

	private String cssLight;
	private String cssDark;
	private String lastHtml;
	
	

	@FXML
	public void initialize() {

		cssLight = loadResourceAsString("/css/webview-light.css");
		cssDark = loadResourceAsString("/css/webview-dark.css");

		// I18n overrides for labels with hardcoded FXML text
		lblLinksIn.setText(I18n.get("label.links.incoming"));

		// Fondo transparente del WebView (JavaFX)
		webContenido.setStyle("-fx-background-color: transparent;");

		// JS bridge: install once, re-injects on every page load
		jsBridge = new JsBridge(this);
		webContenido.getEngine().getLoadWorker().stateProperty().addListener((obs, old, st) -> {
			if (st == Worker.State.SUCCEEDED) {
				try {
					JSObject win = (JSObject) webContenido.getEngine().executeScript("window");
					win.setMember("app", jsBridge);
				} catch (Throwable ignore) {}
			}
		});

		// Si cambian los stylesheets del Scene (tema), re-renderiza con el CSS adecuado
		webContenido.sceneProperty().addListener((obs, old, scene) -> {
			if (scene == null)
				return;
			scene.getStylesheets().addListener((ListChangeListener<String>) c -> {
				if (currentPildora != null && lastHtml != null) {
					renderWithTheme(ThemeManager.load(), lastHtml); // recarga con CSS correcto
				}
			});
		});
		
		webContenido.getEngine().getLoadWorker().stateProperty().addListener((obs, old, st) -> {
	        if (st == javafx.concurrent.Worker.State.SUCCEEDED) {
	            try {
	                webContenido.getEngine().executeScript(
	                    "if (window.MathJax && MathJax.typesetPromise) { MathJax.typesetPromise(); }"
	                );
	            } catch (Exception ignored) {}
	        }
	    });

		// Acciones botones
		btnVolver.setOnAction(e -> {
			if (onClose != null)
				onClose.run();
		});
		btnEditar.setOnAction(e -> {
			if (onEdit != null && currentPildora != null)
				onEdit.accept(currentPildora);
		});
		btnBorrar.setOnAction(e -> doDelete());

		{
			var trash = new FontIcon(FontAwesomeSolid.TRASH);
			trash.setIconSize(16);
			btnBorrar.setText(null);
			btnBorrar.setGraphic(trash);
			btnBorrar.getStyleClass().addAll("icon-btn", "danger", "btn-big"); // <- para color
			btnBorrar.setTooltip(new Tooltip(I18n.get("tooltip.delete")));
		}

		// Botón Editar (icono bolígrafo/cuadro)
		{
			var edit = new FontIcon(FontAwesomeRegular.EDIT);
			edit.setIconSize(16);
			btnEditar.setText(null);
			btnEditar.setGraphic(edit);
			btnEditar.getStyleClass().addAll("icon-btn", "btn-big");
			btnEditar.setTooltip(new Tooltip(I18n.get("tooltip.edit")));
		}

		// Botón Volver (flecha izquierda)
		{
			var back = new FontIcon(FontAwesomeSolid.ARROW_LEFT);
			back.setIconSize(16);
			btnVolver.setText(null);
			btnVolver.setGraphic(back);
			btnVolver.getStyleClass().addAll("icon-btn", "btn-big");
			btnVolver.setTooltip(new Tooltip(I18n.get("tooltip.back")));
		}

		// Instala atajos cuando haya scene (y solo una vez)
		root.sceneProperty().addListener((obs, oldScene, scene) -> {
			if (scene == null || shortcutsInstalados)
				return;
			shortcutsInstalados = true;

			Platform.runLater(root::requestFocus);

			// Filtros sobre el ROOT (no sobre la scene)
			deleteFilter = e -> {
				if (e.getCode() == KeyCode.DELETE) {
					e.consume();
					doDelete();
				}
			};
			escFilter = e -> {
				if (e.getCode() == KeyCode.ESCAPE) {
					e.consume();
					if (onClose != null)
						onClose.run();
				}
			};

			root.addEventFilter(KeyEvent.KEY_PRESSED, deleteFilter);
			root.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);

			searchFilter = e -> {
				if (e.isControlDown() && e.getCode() == KeyCode.F) {
					e.consume();
					toggleSearchBar();
				}
			};
			root.addEventFilter(KeyEvent.KEY_PRESSED, searchFilter);

			// Acelerador Ctrl+E en la SCENE (se retira en dispose())
			scene.getAccelerators().put(kcEditar, () -> btnEditar.fire());
		});

		root.sceneProperty().addListener((obs, oldScene, scene) -> {
			if (scene == null)
				return;
			scene.getStylesheets().addListener((ListChangeListener<String>) c -> repaintTagChipsForTheme());
		});

		setupExportMenu();
	}
	

	private void setupExportMenu() {
		// Icono principal del botón (sin texto)
		var exportIcon = new FontIcon(FontAwesomeSolid.FILE_EXPORT);
		exportIcon.setIconSize(18);
		exportIcon.getStyleClass().add("export-icon");
		btnExportMenu.setText(null);
		btnExportMenu.setGraphic(exportIcon);
		btnExportMenu.setTooltip(new Tooltip(I18n.get("tooltip.export")));
		btnExportMenu.getStyleClass().add("icon-btn");
		btnExportMenu.getStyleClass().add("btn-big");

		// Opción Markdown
		var miMd = new MenuItem(I18n.get("menu.export.markdown"));
		var mdIcon = new FontIcon(FontAwesomeBrands.MARKDOWN);
		mdIcon.setIconSize(16);
		miMd.setGraphic(mdIcon);
		miMd.setOnAction(e -> exportMarkdown()); // llama a tu método existente

		// Opción HTML
		var miHtml = new MenuItem(I18n.get("menu.export.html"));
		var htmlIcon = new FontIcon(FontAwesomeSolid.FILE_CODE);
		htmlIcon.setIconSize(16);
		miHtml.setGraphic(htmlIcon);
		miHtml.setOnAction(e -> exportHtml()); // llama a tu método existente

		// Opción PDF
		var miPdf = new MenuItem(I18n.get("menu.export.pdf"));
		var pdfIcon = new FontIcon(FontAwesomeSolid.FILE_PDF);
		pdfIcon.setIconSize(16);
		miPdf.setGraphic(pdfIcon);
		miPdf.setOnAction(e -> exportPdf());

		btnExportMenu.getItems().setAll(miMd, miHtml, miPdf);
	}

	@FunctionalInterface
	private interface Exporter {
		void run(java.nio.file.Path target) throws Exception;
	}

	private void exportWith(String key, String ext, Exporter exporter) {
		if (currentPildora == null)
			return;
		var chooser = new javafx.stage.FileChooser();
		chooser.setTitle(I18n.get("filechooser.export." + key));
		chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(I18n.get("filechooser.filter." + key), "*." + ext));
		chooser.setInitialFileName(safeFilename(currentPildora.getTitulo()) + "." + ext);
		applyLastExportDir(chooser);
		var file = chooser.showSaveDialog(root.getScene().getWindow());
		if (file == null)
			return;
		saveLastExportDir(file);
		try {
			exporter.run(file.toPath());
			StatusBus.show(I18n.get("status.exported.to", file.getName()), StatusBus.Type.SUCCESS, Duration.seconds(3));
		} catch (Exception ex) {
			String msg = ex.getMessage();
			String text = msg != null && msg.contains("Contenido protegido")
					? I18n.get("status.unlock.before.export")
					: I18n.get("status.export.error", msg);
			StatusBus.show(text, StatusBus.Type.ERROR, Duration.seconds(4));
		}
	}

	private void exportMarkdown() {
		exportWith("markdown", "md", path -> PildoraExporter.exportMarkdown(currentPildora, tagDao, path));
	}

	private void exportHtml() {
		exportWith("html", "html", path -> PildoraExporter.exportHtml(currentPildora, tagDao, path,
				buildChipsCssForHtml(currentPildora.getId())));
	}

	private void exportPdf() {
		exportWith("pdf", "pdf", path -> PildoraExporter.exportPdf(currentPildora, tagDao, path,
				buildChipsCssForHtml(currentPildora.getId())));
	}
	
	private static final java.util.prefs.Preferences EXPORT_PREFS =
			java.util.prefs.Preferences.userNodeForPackage(DetallePildoraController.class);
	private static final String PREF_LAST_EXPORT_DIR = "lastExportDir";

	private void applyLastExportDir(javafx.stage.FileChooser chooser) {
		String dir = EXPORT_PREFS.get(PREF_LAST_EXPORT_DIR, null);
		if (dir != null) {
			var d = new java.io.File(dir);
			if (d.isDirectory()) chooser.setInitialDirectory(d);
		}
	}

	private void saveLastExportDir(java.io.File file) {
		if (file != null && file.getParentFile() != null) {
			EXPORT_PREFS.put(PREF_LAST_EXPORT_DIR, file.getParentFile().getAbsolutePath());
		}
	}

	private HBox buildTagChipButton(String name) {
		name = name == null ? "" : name.trim().toLowerCase();

		var btn = new Button(name);
		btn.getStyleClass().add("tag-chip"); // reutiliza tu CSS del editor (bordes redondeados, etc.)
		btn.setFocusTraversable(false);
		btn.setTooltip(new Tooltip(I18n.get("tooltip.filter.by.tag", name)));

		// Colores según tema (igual que antes)
		boolean dark = ThemeManager.load() == ThemeManager.Theme.DARK;
		String bg = ColorUtil.colorForTag(name, dark);
		String fg = ColorUtil.bestTextOn(bg);
		btn.setStyle(String.format("-chip-bg: %s; -chip-fg: %s;", bg, fg));

		// Acción: invocar callback si existe
		final String nameFinal = name;
		btn.setOnAction(e -> {
			if (onTagClick != null)
				onTagClick.accept(nameFinal);
		});

		// Envolvemos en HBox para mantener la misma jerarquía esperada por
		// repaintTagChipsForTheme()
		var box = new HBox(btn);
		box.getStyleClass().add("tag-chip-wrapper");
		return box;
	}

	private void repaintTagChipsForTheme() {
		boolean dark = ThemeManager.load() == ThemeManager.Theme.DARK;
		for (var n : tagsBox.getChildren()) {
			if (n instanceof HBox box && !box.getChildren().isEmpty()
					&& box.getChildren().get(0) instanceof Button btn) {
				String name = btn.getText();
				String bg = ColorUtil.colorForTag(name, dark);
				String fg = ColorUtil.bestTextOn(bg);
				btn.setStyle(String.format("-chip-bg: %s; -chip-fg: %s;", bg, fg));
			}
		}
	}

	public void setOnTagClick(Consumer<String> onTagClick) {
		this.onTagClick = onTagClick;
	}

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	public void setOnEdit(Consumer<Pildora> onEdit) {
		this.onEdit = onEdit;
	}

	public void setOnDelete(Consumer<Pildora> onDelete) {
		this.onDelete = onDelete;
	}

	private String buildChipsCssForHtml(int pildoraId) {
		var tags = tagDao.findByPildoraId(pildoraId);
		boolean dark = false; // el HTML por defecto es claro; pon true si quieres tema oscuro

		StringBuilder css = new StringBuilder();
		for (var t : tags) {
			String name = t.getNombre();
			String bg = ColorUtil.colorForTag(name, dark);
			String fg = ColorUtil.bestTextOn(bg);
			// usa selector por data-tag exacto
			css.append(".chip[data-tag=\"").append(name).append("\"]{").append("background:").append(bg).append(";")
					.append("color:").append(fg).append(";").append("}");
		}
		return css.toString();
	}

	private static String safeFilename(String s) {
		String base = (s == null || s.isBlank()) ? "pildora" : s.trim();
		base = base.replaceAll("[\\\\/:*?\"<>|]+", "_");
		return base.length() > 60 ? base.substring(0, 60) : base;
	}

	private void doDelete() {
		if (currentPildora == null)
			return;

		if (currentPildora.isProtegida()) {
			var sec = io.github.guillermo_david.security.SecurityService.getInstance();
			if (!sec.isUnlocked()) {
				// mini helper local (mismo patrón que en Listado)
				if (!unlockWithFeedback()) {
					StatusBus.show(I18n.get("status.cannot.delete.without.pin"), StatusBus.Type.ERROR, Duration.seconds(3));
					return;
				}
			}
		}

		if (deletingNow || onDelete == null || currentPildora == null)
			return;
		deletingNow = true;
		try {
			onDelete.accept(currentPildora);
		} finally {
			// pequeño “debounce” por si hay autorepeat
			PauseTransition pt = new javafx.animation.PauseTransition(Duration.millis(150));
			pt.setOnFinished(ev -> deletingNow = false);
			pt.play();
		}
	}

	private boolean unlockWithFeedback() {
		return SecurityUiHelper.unlockWithFeedback(root);
	}

	public void mostrarPildora(Pildora p) {
		this.currentPildora = p;
		
		loadRefsSection(p.getId());

		lblTitulo.setText(p.getTitulo());
		lblFecha.setText(I18n.get("detail.created.date",
				p.getFechaCreacion() != null
				? p.getFechaCreacion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
				: I18n.get("detail.no.date")));

		tagsBox.getChildren().clear();
		for (Tag t : tagDao.findByPildoraId(p.getId())) {
			tagsBox.getChildren().add(buildTagChipButton(t.getNombre()));
		}
		repaintTagChipsForTheme();

		renderContenido(p);
		
	}
	
	private Button chipForLink(io.github.guillermo_david.model.Pildora p) {
	    var b = new Button(p.getTitulo() == null ? ("#" + p.getId()) : p.getTitulo());
	    b.getStyleClass().addAll("tag-chip");
	    b.setOnAction(e -> openPildoraFromJs(p.getId()));
	    return b;
	}

	private boolean linksCollapsed = true;
	private java.util.List<javafx.scene.Node> allBacklinkChips;

	private void loadRefsSection(int currentId) {
	    var dao = new PildoraLinkDao();
	    var inb = dao.listIncomingRefs(currentId);

	    boolean hasLinks = !inb.isEmpty();
	    linksSection.setVisible(hasLinks);
	    linksSection.setManaged(hasLinks);

	    if (!hasLinks) return;

	    allBacklinkChips = inb.stream()
	        .map(p -> (javafx.scene.Node) chipForLink(p))
	        .collect(java.util.stream.Collectors.toList());
	    linksInBox.getChildren().setAll(allBacklinkChips);

	    linksCollapsed = true;
	    btnShowMoreLinks.setVisible(false);
	    btnShowMoreLinks.setManaged(false);

	    Platform.runLater(() -> {
	        linksInBox.applyCss();
	        linksInBox.layout();
	        double twoRowsMax = 2 * (32 + linksInBox.getVgap()) + linksInBox.getVgap();
	        if (linksInBox.getHeight() > twoRowsMax) {
	            collapseLinks(twoRowsMax, inb.size());
	        }
	    });
	}

	private void collapseLinks(double maxHeight, int total) {
	    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
	    clip.widthProperty().bind(linksInBox.widthProperty());
	    clip.setHeight(maxHeight);
	    linksInBox.setClip(clip);
	    linksCollapsed = true;

	    int visible = countVisibleInRows(2);
	    int hidden = total - visible;
	    btnShowMoreLinks.setText(I18n.get("label.show.more", hidden));
	    btnShowMoreLinks.setVisible(true);
	    btnShowMoreLinks.setManaged(true);
	    btnShowMoreLinks.setOnAction(e -> {
	        if (linksCollapsed) {
	            linksInBox.setClip(null);
	            btnShowMoreLinks.setText(I18n.get("label.show.less"));
	            linksCollapsed = false;
	        } else {
	            linksInBox.setClip(clip);
	            btnShowMoreLinks.setText(I18n.get("label.show.more", hidden));
	            linksCollapsed = true;
	        }
	    });
	}

	private int countVisibleInRows(int maxRows) {
	    double width = linksInBox.getWidth();
	    if (width <= 0) return allBacklinkChips.size();
	    double hgap = linksInBox.getHgap();
	    int rows = 1;
	    double x = 0;
	    int count = 0;
	    for (var chip : allBacklinkChips) {
	        double cw = chip.prefWidth(-1);
	        if (x + cw > width && x > 0) {
	            rows++;
	            if (rows > maxRows) break;
	            x = 0;
	        }
	        x += cw + hgap;
	        count++;
	    }
	    return count;
	}
	
	private void renderContenido(Pildora p) {
		try {
			String bodyMd;
			if (p.isProtegida()) {
				if (!security.isUnlocked()) {
					// No intentes desbloquear aquí; el listado es la puerta
					bodyMd = I18n.get("detail.content.protected");
				} else {
					bodyMd = security.decrypt(p.getDescripcionCipher(), p.getDescripcionIv());
				}
			} else {
				bodyMd = p.getDescripcion();
			}

			lastHtml = MD.toHtml(bodyMd);
			renderWithTheme(ThemeManager.load(), lastHtml);

		} catch (Exception ex) {
			lastHtml = "<p><em>" + I18n.get("detail.content.load.error") + "</em></p>";
			renderWithTheme(ThemeManager.load(), lastHtml);
		}
	}

	private void renderWithTheme(ThemeManager.Theme t, String bodyHtml) {
	    String css = (t == ThemeManager.Theme.DARK) ? cssDark : cssLight;

	    String html = wrapHtmlWithCss(lastHtml, css, /*mathjax*/true, /*interceptLinks*/true);
	    webContenido.getEngine().loadContent(html);
	}
	
	private void openPildoraFromJs(int id) {
	    try {
	        var dao = new io.github.guillermo_david.dao.PildoraDao();
	        var target = dao.buscarPorId(id);
	        if (target == null) {
	            StatusBus.show(I18n.get("status.pildora.not.found", id), StatusBus.Type.WARN, Duration.seconds(3));
	            return;
	        }
	        if (target.isProtegida() && !security.isUnlocked()) {
	            if (!security.ensureUnlocked(() -> PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10)))) {
	                return;
	            }
	        }
	        mostrarPildora(target); // navega dentro del propio detalle
	    } catch (Exception ex) {
	        StatusBus.show(I18n.get("status.pildora.open.failed"), StatusBus.Type.ERROR, Duration.seconds(3));
	    }
	}

	private static final java.util.regex.Pattern P_PILDORA_HREF =
	    java.util.regex.Pattern.compile("href=\"[pP]ildora:(\\d+)\"");

	private String wrapHtmlWithCss(String bodyHtml, String css, boolean mathjax, boolean interceptLinks) {
	    if (interceptLinks) {
	        bodyHtml = P_PILDORA_HREF.matcher(bodyHtml).replaceAll(
	            "href=\"#\" onclick=\"if(window.app)try{window.app.openPildora($1)}catch(e){};return false;\"");
	    }
	    String intercept = "";

	    String mj = !mathjax ? "" : """
	      <script>
	        window.MathJax = {
	          tex: { inlineMath: [['$', '$'], ['\\\\(', '\\\\)']], displayMath: [['$$','$$'], ['\\\\[','\\\\]']] },
	          options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },
	          svg: { fontCache: 'global' }
	        };
	      </script>
	      <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
	    """;

	    return """
	      <!doctype html>
	      <html>
	        <head>
	          <meta charset="UTF-8">
	          <meta name="color-scheme" content="dark light">
	          <style>%s</style>
	        </head>
	        <body>%s%s%s</body>
	      </html>
	    """.formatted(css, bodyHtml, intercept, mj);
	}

	private String loadResourceAsString(String path) {
		try (var is = getClass().getResourceAsStream(path)) {
			if (is == null)
				return "";
			return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	private void toggleSearchBar() {
		if (searchBar == null) {
			searchField = new javafx.scene.control.TextField();
			searchField.setPromptText(I18n.get("search.prompt"));
			searchField.setPrefWidth(250);
			searchStatus = new Label();
			searchStatus.setStyle("-fx-opacity: 0.6; -fx-font-size: 11px;");
			var closeBtn = new Button("X");
			closeBtn.getStyleClass().add("icon-btn");
			closeBtn.setOnAction(e -> hideSearchBar());
			searchBar = new javafx.scene.layout.HBox(8, searchField, searchStatus, closeBtn);
			searchBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
			searchBar.setPadding(new javafx.geometry.Insets(4, 10, 4, 10));
			searchBar.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border; -fx-border-width: 0 0 1 0;");

			searchField.setOnAction(e -> doSearch(false));
			searchField.setOnKeyPressed(e -> {
				if (e.getCode() == KeyCode.ESCAPE) {
					e.consume();
					hideSearchBar();
				}
			});

			PauseTransition debounce = new PauseTransition(Duration.millis(300));
			debounce.setOnFinished(e -> doSearch(false));
			searchField.textProperty().addListener((o, a, b) -> debounce.playFromStart());
		}

		int idx = root.getChildren().indexOf(webContenido);
		if (!root.getChildren().contains(searchBar)) {
			root.getChildren().add(idx, searchBar);
		}
		searchField.requestFocus();
		searchField.selectAll();
	}

	private void hideSearchBar() {
		if (searchBar != null) {
			root.getChildren().remove(searchBar);
			clearSearchHighlights();
			webContenido.requestFocus();
		}
	}

	private void doSearch(boolean backwards) {
		String query = searchField.getText();
		if (query == null || query.isBlank()) {
			clearSearchHighlights();
			searchStatus.setText("");
			return;
		}
		try {
			Object result = webContenido.getEngine().executeScript(
				"window.find('" + query.replace("'", "\\'").replace("\\", "\\\\") + "', false, " + backwards + ", true)");
			boolean found = Boolean.TRUE.equals(result);
			searchStatus.setText(found ? "" : I18n.get("search.no.results"));
		} catch (Exception ignored) {
			searchStatus.setText("");
		}
	}

	private void clearSearchHighlights() {
		try {
			webContenido.getEngine().executeScript("window.getSelection().removeAllRanges()");
		} catch (Exception ignored) {}
	}

	public void dispose() {
		try {
			if (deleteFilter != null)
				root.removeEventFilter(KeyEvent.KEY_PRESSED, deleteFilter);
			if (escFilter != null)
				root.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
			if (searchFilter != null)
				root.removeEventFilter(KeyEvent.KEY_PRESSED, searchFilter);
			var scene = root.getScene();
			if (scene != null)
				scene.getAccelerators().remove(kcEditar);
		} catch (Exception ignored) {
		}
	}
}
