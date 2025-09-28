package io.github.guillermo_david.controller;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.StatusBus.Type;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

public class EditorPildoraController {

    @FXML
    private TextField txtTitulo;

    @FXML
    private TextArea txtDescripcion;

    @FXML
    private TextField txtTags;

    @FXML
    private Button btnGuardar, btnCancelar;
    
    private Runnable onClose;
    private Pildora pildoraEnEdicion;

    private final PildoraDao pildoraDao = new PildoraDao();
    private final TagDao tagDao = new TagDao();
    private final PildoraTagDao pildoraTagDao = new PildoraTagDao();
    
    private EventHandler<KeyEvent> editorKeyHandler; // guardamos la referencia para no duplicar

    @FXML
    public void initialize() {
    	btnCancelar.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });

        btnGuardar.setOnAction(e -> {
            String titulo = txtTitulo.getText().trim();
            String descripcion = txtDescripcion.getText().trim();
            String tags = txtTags.getText().trim();

            if (titulo.isEmpty() || descripcion.isEmpty()) {
            	StatusBus.show("Título y descripción son obligatorios.", Type.WARN, Duration.seconds(4));
                return;
            }

            if (pildoraEnEdicion == null) {
                // Nueva
                Pildora nueva = new Pildora(titulo, descripcion);
                pildoraDao.insertar(nueva);
                guardarTags(nueva, tags);
            } else {
                // Editar
                pildoraEnEdicion.setTitulo(titulo);
                pildoraEnEdicion.setDescripcion(descripcion);
                pildoraDao.actualizar(pildoraEnEdicion);
                guardarTags(pildoraEnEdicion, tags);
            }

            StatusBus.show("Píldora guardada correctamente.", Type.SUCCESS, Duration.seconds(3));
            if (onClose != null) onClose.run();
        });
        
        // (Opcional) Tooltips con pistas
        btnGuardar.setTooltip(new Tooltip("Guardar (Ctrl+S)"));
        btnCancelar.setTooltip(new Tooltip("Cancelar (Esc)"));
    }
    
    public void instalarAtajosEn(Node rootNode) {
        // Si ya había un handler instalado en este nodo, lo quitamos para evitar duplicados
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
            if (e.isControlDown() && e.getCode() == KeyCode.B) { insertarTexto("**negrita**"); e.consume(); return; }
            if (e.isControlDown() && e.getCode() == KeyCode.I) { insertarTexto("*cursiva*"); e.consume(); return; }
            if (e.isControlDown() && e.getCode() == KeyCode.K) { insertarTexto("[texto](https://)"); e.consume(); return; }
            if (e.isControlDown() && e.getCode() == KeyCode.E) { insertarTexto("`código`"); e.consume(); return; }
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
    
    private void guardarTags(Pildora p, String tags) {
        // Primero quitamos todos los tags actuales
        pildoraTagDao.removeAllTagsFromPildora(p.getId());

        // Luego insertamos los nuevos (si hay)
        if (tags != null && !tags.isEmpty()) {
            Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .forEach(nombreTag -> {
                        Tag tag = tagDao.findOrCreate(nombreTag);
                        pildoraTagDao.addTagToPildora(p.getId(), tag.getId());
                    });
        }
    }

    public void cargarPildora(Pildora p) {
        this.pildoraEnEdicion = p;
        txtTitulo.setText(p.getTitulo());
        txtDescripcion.setText(p.getDescripcion());
        txtTags.setText(tagDao.findByPildoraId(p.getId())
                              .stream().map(Tag::getNombre)
                              .collect(Collectors.joining(", ")));
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }
}
