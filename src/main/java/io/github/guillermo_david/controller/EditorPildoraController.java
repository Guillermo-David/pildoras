package io.github.guillermo_david.controller;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.ColorUtil;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.javafx.StatusBus.Type;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.Duration;

public class EditorPildoraController {

	@FXML private TextField txtTagInput;
	@FXML private FlowPane tagsBox;
	@FXML private TextField txtTitulo;
	@FXML private TextArea txtDescripcion;
	@FXML private Button btnGuardar, btnCancelar;

	private Runnable onClose;
	private Pildora pildoraEnEdicion;

	private final PildoraDao pildoraDao = new PildoraDao();
	private final TagDao tagDao = new TagDao();
	private final PildoraTagDao pildoraTagDao = new PildoraTagDao();

	private AutoCompletionBinding<String> tagAuto;
	private EventHandler<KeyEvent> editorKeyHandler;
	private final Set<String> currentTags = new LinkedHashSet<>();

	@FXML
	public void initialize() {
		btnCancelar.setOnAction(e -> {
			if (onClose != null)
				onClose.run();
		});

		btnGuardar.setOnAction(e -> {
		    String titulo = txtTitulo.getText().trim();
		    String descripcion = txtDescripcion.getText().trim();

		    if (titulo.isEmpty() || descripcion.isEmpty()) {
		        StatusBus.show("Título y descripción son obligatorios.", Type.WARN, Duration.seconds(4));
		        return;
		    }

		    if (pildoraEnEdicion == null) {
		        Pildora nueva = new Pildora(titulo, descripcion);
		        pildoraDao.insertar(nueva);
		        persistTags(nueva.getId());
		    } else {
		        pildoraEnEdicion.setTitulo(titulo);
		        pildoraEnEdicion.setDescripcion(descripcion);
		        pildoraDao.actualizar(pildoraEnEdicion);
		        persistTags(pildoraEnEdicion.getId());
		    }

		    StatusBus.show("Píldora guardada correctamente.", Type.SUCCESS, Duration.seconds(3));
		    if (onClose != null) onClose.run();
		});

		// (Opcional) Tooltips con pistas
		btnGuardar.setTooltip(new Tooltip("Guardar (Ctrl+S)"));
		btnCancelar.setTooltip(new Tooltip("Cancelar (Esc)"));

		// Autocompletado
		tagAuto = TextFields.bindAutoCompletion(
		    txtTagInput,
		    (Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>>) req -> {
		        String q = req.getUserText();
		        if (q == null || q.isBlank()) return List.of();
		        return tagDao.listAllLike(q.toLowerCase(), 20).stream()
		                     .map(String::toLowerCase)
		                     .toList();
		    });
		tagAuto.setOnAutoCompleted(ev -> {
		    addTagChip(ev.getCompletion().toLowerCase());
		    txtTagInput.clear();
		});

		// Enter
		txtTagInput.setOnAction(e -> commitTagFromInput());

		// Coma (layout-safe)
		txtTagInput.addEventFilter(KeyEvent.KEY_TYPED, e -> {
		    if (",".equals(e.getCharacter())) {
		        e.consume();
		        commitTagFromInput();
		    }
		});

		// Backspace elimina último chip si input vacío
		txtTagInput.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
		    if (e.getCode() == KeyCode.BACK_SPACE && txtTagInput.getText().isBlank() && !currentTags.isEmpty()) {
		        String last = null;
		        for (String t : currentTags) last = t;
		        if (last != null) {
		            currentTags.remove(last);
		            int n = tagsBox.getChildren().size();
		            if (n > 0) tagsBox.getChildren().remove(n - 1);
		            e.consume();
		        }
		    }
		});

		tagsBox.sceneProperty().addListener((obs, oldScene, scene) -> {
		    if (scene == null) return;
		    scene.getStylesheets().addListener(new ListChangeListener<String>() {
		        @Override public void onChanged(Change<? extends String> c) {
		            recolorChips();
		        }
		    });
		});

	}
	
	private void recolorChips() {
	    boolean dark = (ThemeManager.load() == ThemeManager.Theme.DARK);
	    tagsBox.getChildren().forEach(n -> {
	        if (n instanceof HBox box && !box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
	            String name = lbl.getText();
	            String bg = ColorUtil.colorForTag(name, dark);
	            String fg = ColorUtil.bestTextOn(bg);
	            box.setStyle(String.format("-fx-tag-bg: %s; -fx-tag-fg: %s;", bg, fg));
	        }
	    });
	}


	private void commitTagFromInput() {
	    String raw = txtTagInput.getText();
	    if (raw == null) return;

	    for (String part : raw.split(",")) {
	        String t = part.trim().replaceAll("\\s+", " ").toLowerCase();
	        if (!t.isEmpty()) addTagChip(t);
	    }
	    txtTagInput.clear();
	}

	private void addTagChip(String name) {
		name = name.toLowerCase();
		if (!currentTags.add(name))
			return;
		var chip = buildTagChip(name);
		tagsBox.getChildren().add(chip);
	}

	private HBox buildTagChip(String name) {
		var lb = new Label(name);
		var btnX = new Button("✕");
		btnX.getStyleClass().add("chip-close");
		btnX.setOnAction(e -> {
			currentTags.remove(name);
			tagsBox.getChildren().remove(((Node) e.getSource()).getParent());
		});

		var box = new HBox(6, lb, btnX);
		box.getStyleClass().add("tag-chip");

		String bg = ColorUtil.colorForTag(name, ThemeManager.load() == ThemeManager.Theme.DARK);
		String fg = ColorUtil.bestTextOn(bg);
		box.setStyle(String.format("-fx-tag-bg: %s; -fx-tag-fg: %s;", bg, fg));
		return box;
	}

	private void persistTags(int pildoraId) {
	    pildoraTagDao.removeAllTagsFromPildora(pildoraId);
	    for (String t : currentTags) {
	        Tag tag = tagDao.findOrCreate(t);
	        pildoraTagDao.addTagToPildora(pildoraId, tag.getId());
	    }
	}

	public void instalarAtajosEn(Node rootNode) {
		// Si ya había un handler instalado en este nodo, lo quitamos para evitar
		// duplicados
		if (editorKeyHandler != null) {
			rootNode.removeEventFilter(KeyEvent.KEY_PRESSED, editorKeyHandler);
		}

		editorKeyHandler = e -> {
			// Ctrl+S → Guardar
			if (e.isControlDown() && e.getCode() == KeyCode.S) {
				btnGuardar.fire();
				e.consume();
				return;
			}
			// Esc → Cancelar
			if (e.getCode() == KeyCode.ESCAPE) {
				btnCancelar.fire();
				e.consume();
				return;
			}
			// Markdown rápidos
			if (e.isControlDown() && e.getCode() == KeyCode.B) {
				insertarTexto("**negrita**");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.I) {
				insertarTexto("*cursiva*");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.K) {
				insertarTexto("[texto](https://)");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.E) {
				insertarTexto("`código`");
				e.consume();
				return;
			}
		};

		rootNode.addEventFilter(KeyEvent.KEY_PRESSED, editorKeyHandler);
	}

	@FXML
	private void insertBold() {
		insertarTexto("**negrita**");
	}

	@FXML
	private void insertItalic() {
		insertarTexto("*cursiva*");
	}

	@FXML
	private void insertLink() {
		insertarTexto("[texto](https://)");
	}

	@FXML
	private void insertCode() {
		insertarTexto("`código`");
	}

	@FXML
	private void mostrarAyuda() {
		Alert ayuda = new Alert(Alert.AlertType.INFORMATION);
		ayuda.setTitle("Ayuda Markdown");
		ayuda.setHeaderText("Sintaxis básica");
		ayuda.setContentText("""
				**negrita** → texto en negrita
				*cursiva* → texto en cursiva
				[enlace](https://) → hipervínculo
				`código` → fragmento de código
				""");
		ayuda.showAndWait();
	}

	private void insertarTexto(String snippet) {
		int pos = txtDescripcion.getCaretPosition();
		txtDescripcion.insertText(pos, snippet);
		txtDescripcion.requestFocus();
	}

	public void cargarPildora(Pildora p) {
	    this.pildoraEnEdicion = p;
	    txtTitulo.setText(p.getTitulo());
	    txtDescripcion.setText(p.getDescripcion());

	    currentTags.clear();
	    tagsBox.getChildren().clear();
	    tagDao.findByPildoraId(p.getId()).forEach(t -> addTagChip(t.getNombre()));
	}

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}
}
