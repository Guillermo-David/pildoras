package io.github.guillermo_david.controller;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.theme.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Duration;

public class DetallePildoraController {

	@FXML private VBox root; 
    @FXML private Label lblTitulo, lblFecha, lblTags;
    @FXML private WebView webContenido;
    @FXML private Button btnVolver, btnEditar, btnBorrar;

    private Runnable onClose;
    private Consumer<Pildora> onEdit;
    private Consumer<Pildora> onDelete;
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
        cssDark  = loadResourceAsString("/css/webview-dark.css");

        // Fondo transparente del WebView (JavaFX)
        webContenido.setStyle("-fx-background-color: transparent;");

        // Si cambian los stylesheets del Scene (tema), re-renderiza con el CSS adecuado
        webContenido.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) return;
            scene.getStylesheets().addListener((ListChangeListener<String>) c -> {
                if (currentPildora != null && lastHtml != null) {
                    renderWithTheme(ThemeManager.load(), lastHtml); // recarga con CSS correcto
                }
            });
        });
    	
    	// Acciones botones
    	btnVolver.setOnAction(e -> { if (onClose != null) onClose.run(); });
    	btnEditar.setOnAction(e -> { if (onEdit != null && currentPildora != null) onEdit.accept(currentPildora); });
    	btnBorrar.setOnAction(e -> doDelete());
    	
    	// Tooltips
    	btnVolver.setTooltip(new Tooltip("Volver (Esc)"));
    	btnEditar.setTooltip(new Tooltip("Editar (Ctrl+E)"));
    	btnBorrar.setTooltip(new Tooltip("Borrar (Supr)"));
    	
    	// Instala atajos cuando haya scene (y solo una vez)
    	root.sceneProperty().addListener((obs, oldScene, scene) -> {
    		if (scene == null || shortcutsInstalados) return;
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
    				if (onClose != null) onClose.run();
    			}
    		};
    		
    		root.addEventFilter(KeyEvent.KEY_PRESSED, deleteFilter);
    		root.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
    		
    		// Acelerador Ctrl+E en la SCENE (se retira en dispose())
    		scene.getAccelerators().put(kcEditar, () -> btnEditar.fire());
    	});
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

    
    private void doDelete() {
        if (deletingNow || onDelete == null || currentPildora == null) return;
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

    public void mostrarPildora(Pildora p) {
        this.currentPildora = p;
        
        
        lblTitulo.setText(p.getTitulo());
        lblFecha.setText("Creada: " + (p.getFechaCreacion() != null
                ? p.getFechaCreacion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "sin fecha"));

        var tags = new TagDao().findByPildoraId(p.getId());
        lblTags.setText("Tags: " + tags.stream().map(Tag::getNombre).collect(Collectors.joining(", ")));

        // Render Markdown -> HTML

        Node doc = parser.parse(p.getDescripcion());
        lastHtml = renderer.render(doc);                  // guarda el html
        renderWithTheme(ThemeManager.load(), lastHtml);
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
            if (is == null) return "";
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public void dispose() {
        try {
            if (deleteFilter != null) root.removeEventFilter(KeyEvent.KEY_PRESSED, deleteFilter);
            if (escFilter != null)    root.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            var scene = root.getScene();
            if (scene != null) scene.getAccelerators().remove(kcEditar);
        } catch (Exception ignored) {}
    }
}
