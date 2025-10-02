package io.github.guillermo_david.controller;

import java.util.function.Consumer;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.export.PildoraExporter;
import io.github.guillermo_david.javafx.ColorUtil;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
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

public class DetallePildoraController {

	@FXML
	private VBox root;
	@FXML
	private Label lblTitulo, lblFecha;
	@FXML
	private FlowPane tagsBox;
	@FXML
	private WebView webContenido;
	@FXML
	private Button btnVolver, btnEditar, btnBorrar;
	@FXML
	private MenuButton btnExportMenu;

	private final SecurityService security = SecurityService.getInstance();

	private Runnable onClose;
	private Consumer<Pildora> onEdit;
	private Consumer<Pildora> onDelete;
	private Consumer<String> onTagClick;
	private Pildora currentPildora;

	private EventHandler<KeyEvent> deleteFilter;
	private EventHandler<KeyEvent> escFilter;
	private KeyCombination kcEditar = new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN);

	private final Parser parser = Parser.builder().build();
	private final HtmlRenderer renderer = HtmlRenderer.builder().build();

	private boolean shortcutsInstalados = false;
	private boolean deletingNow = false;

	private String cssLight;
	private String cssDark;
	private String lastHtml;

	@FXML
	public void initialize() {

		cssLight = loadResourceAsString("/css/webview-light.css");
		cssDark = loadResourceAsString("/css/webview-dark.css");

		// Fondo transparente del WebView (JavaFX)
		webContenido.setStyle("-fx-background-color: transparent;");

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
			btnBorrar.setTooltip(new Tooltip("Eliminar (Supr)"));
		}

		// Botón Editar (icono bolígrafo/cuadro)
		{
			var edit = new FontIcon(FontAwesomeRegular.EDIT);
			edit.setIconSize(16);
			btnEditar.setText(null);
			btnEditar.setGraphic(edit);
			btnEditar.getStyleClass().addAll("icon-btn", "btn-big");
			btnEditar.setTooltip(new Tooltip("Editar (Ctrl+E)"));
		}

		// Botón Volver (flecha izquierda)
		{
			var back = new FontIcon(FontAwesomeSolid.ARROW_LEFT);
			back.setIconSize(16);
			btnVolver.setText(null);
			btnVolver.setGraphic(back);
			btnVolver.getStyleClass().addAll("icon-btn", "btn-big");
			btnVolver.setTooltip(new Tooltip("Volver (Esc)"));
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
		btnExportMenu.setTooltip(new Tooltip("Exportar"));
		btnExportMenu.getStyleClass().add("icon-btn");
		btnExportMenu.getStyleClass().add("btn-big");

		// Opción Markdown
		var miMd = new MenuItem("Markdown");
		var mdIcon = new FontIcon(FontAwesomeBrands.MARKDOWN);
		mdIcon.setIconSize(16);
		miMd.setGraphic(mdIcon);
		miMd.setOnAction(e -> exportMarkdown()); // llama a tu método existente

		// Opción HTML
		var miHtml = new MenuItem("HTML");
		var htmlIcon = new FontIcon(FontAwesomeSolid.FILE_CODE);
		htmlIcon.setIconSize(16);
		miHtml.setGraphic(htmlIcon);
		miHtml.setOnAction(e -> exportHtml()); // llama a tu método existente

		btnExportMenu.getItems().setAll(miMd, miHtml);
	}

	private void exportMarkdown() {
		if (currentPildora == null)
			return;
		var chooser = new javafx.stage.FileChooser();
		chooser.setTitle("Exportar a Markdown");
		chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Markdown (*.md)", "*.md"));
		chooser.setInitialFileName(safeFilename(currentPildora.getTitulo()) + ".md");
		var file = chooser.showSaveDialog(root.getScene().getWindow());
		if (file == null)
			return;
		try {
			PildoraExporter.exportMarkdown(currentPildora, new TagDao(), file.toPath());
			StatusBus.show("Exportada a " + file.getName(), StatusBus.Type.SUCCESS, javafx.util.Duration.seconds(3));
		} catch (Exception ex) {
			ex.printStackTrace();
			StatusBus.show("Error al exportar: " + ex.getMessage(), StatusBus.Type.ERROR,
					javafx.util.Duration.seconds(4));
		}
	}

	private void exportHtml() {
		if (currentPildora == null)
			return;
		var chooser = new javafx.stage.FileChooser();
		chooser.setTitle("Exportar a HTML");
		chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("HTML (*.html)", "*.html"));
		chooser.setInitialFileName(safeFilename(currentPildora.getTitulo()) + ".html");
		var file = chooser.showSaveDialog(root.getScene().getWindow());
		if (file == null)
			return;
		try {
			// Si quieres colores de chips idénticos a la app, puedes pasar un CSS generado
			// aquí (opcional)
			var extraCss = buildChipsCssForHtml(currentPildora.getId()); // 👈 mismo color que en la app
			PildoraExporter.exportHtml(currentPildora, new TagDao(), file.toPath(), extraCss);
			StatusBus.show("Exportada a " + file.getName(), StatusBus.Type.SUCCESS, javafx.util.Duration.seconds(3));
		} catch (Exception ex) {
			ex.printStackTrace();
			StatusBus.show("Error al exportar: " + ex.getMessage(), StatusBus.Type.ERROR,
					javafx.util.Duration.seconds(4));
		}
	}

	private HBox buildTagChipButton(String name) {
		name = name == null ? "" : name.trim().toLowerCase();

		var btn = new Button(name);
		btn.getStyleClass().add("tag-chip"); // reutiliza tu CSS del editor (bordes redondeados, etc.)
		btn.setFocusTraversable(false);
		btn.setTooltip(new Tooltip("Filtrar por tag: " + name));

		// Colores según tema (igual que antes)
		boolean dark = ThemeManager.load() == ThemeManager.Theme.DARK;
		String bg = ColorUtil.colorForTag(name, dark);
		String fg = ColorUtil.bestTextOn(bg);
		btn.setStyle(String.format("-fx-tag-bg:%s; -fx-tag-fg:%s;", bg, fg));

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
				btn.setStyle(String.format("-fx-tag-bg:%s; -fx-tag-fg:%s;", bg, fg));
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
		var tagDao = new TagDao();
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
					StatusBus.show("No puedes borrar sin PIN.", StatusBus.Type.ERROR, Duration.seconds(3));
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
		var security = io.github.guillermo_david.security.SecurityService.getInstance();
		if (security.isUnlocked())
			return true;

		long lockMs = security.lockoutRemainingMillis();
		if (lockMs > 0) {
			StatusBus.show("Has agotado los intentos. Vuelve a probar en " + humanize(lockMs) + ".",
					StatusBus.Type.ERROR, Duration.seconds(4));
			return false;
		}
		char[] pin = PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10));
		if (pin == null)
			return false;
		try {
			if (security.verifyPin(pin)) {
				StatusBus.show("Desbloqueado.", StatusBus.Type.INFO, Duration.seconds(2));
				return true;
			} else {
				if (security.isLockedOut()) {
					long ms = security.lockoutRemainingMillis();
					StatusBus.show("Has agotado los intentos. Vuelve a probar en " + humanize(ms) + ".",
							StatusBus.Type.ERROR, Duration.seconds(4));
				} else {
					int left = security.attemptsLeft();
					StatusBus.show("PIN incorrecto. Te quedan " + left + " intento" + (left == 1 ? "" : "s") + ".",
							StatusBus.Type.ERROR, Duration.seconds(3));
				}
				return false;
			}
		} finally {
			java.util.Arrays.fill(pin, '\0');
		}
	}

	private static String humanize(long ms) {
		long s = (ms + 999) / 1000;
		if (s < 60)
			return s + " s";
		long m = s / 60, rs = s % 60;
		return rs == 0 ? (m + " min") : (m + " min " + rs + " s");
	}

	public void mostrarPildora(Pildora p) {
		this.currentPildora = p;

		lblTitulo.setText(p.getTitulo());
		lblFecha.setText("Creada: " + (p.getFechaCreacion() != null
				? p.getFechaCreacion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
				: "sin fecha"));

		tagsBox.getChildren().clear();
		for (Tag t : new TagDao().findByPildoraId(p.getId())) {
			tagsBox.getChildren().add(buildTagChipButton(t.getNombre()));
		}
		repaintTagChipsForTheme();

		renderContenido(p);

	}

	private void renderContenido(Pildora p) {
		try {
			String bodyMd;
			if (p.isProtegida()) {
				if (!security.isUnlocked()) {
					// No intentes desbloquear aquí; el listado es la puerta
					bodyMd = "_Contenido protegido_";
				} else {
					bodyMd = security.decrypt(p.getDescripcionCipher(), p.getDescripcionIv());
				}
			} else {
				bodyMd = p.getDescripcion();
			}

			Node doc = parser.parse(bodyMd == null ? "" : bodyMd);
			lastHtml = renderer.render(doc);
			renderWithTheme(ThemeManager.load(), lastHtml);

		} catch (Exception ex) {
			lastHtml = "<p><em>Error al cargar contenido.</em></p>";
			renderWithTheme(ThemeManager.load(), lastHtml);
		}
	}

	private void renderWithTheme(ThemeManager.Theme t, String bodyHtml) {
		String css = (t == ThemeManager.Theme.DARK) ? cssDark : cssLight;
		webContenido.getEngine().loadContent(wrapHtmlWithCss(bodyHtml, css));
	}

	/** Envuélvelo con HTML + <style> CSS incrustado y fondo transparente */
	private String wrapHtmlWithCss(String bodyHtml, String css) {
		return """
				  <!doctype html>
				  <html>
				    <head>
				      <meta charset="UTF-8">
				      <meta name="color-scheme" content="dark light">
				      <style>%s</style>
				    </head>
				    <body>%s</body>
				  </html>
				""".formatted(css, bodyHtml);
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

	public void dispose() {
		try {
			if (deleteFilter != null)
				root.removeEventFilter(KeyEvent.KEY_PRESSED, deleteFilter);
			if (escFilter != null)
				root.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
			var scene = root.getScene();
			if (scene != null)
				scene.getAccelerators().remove(kcEditar);
		} catch (Exception ignored) {
		}
	}
}
