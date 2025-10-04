package io.github.guillermo_david.javafx;

import java.util.function.Consumer;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.dao.DraftDao;
import io.github.guillermo_david.model.Draft;
import io.github.guillermo_david.security.SecurityService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class DraftDialogs {
    private DraftDialogs(){}

    // === Resultado del diálogo rápido ===
    public enum Action { SAVE, CONVERT, CANCEL }

    public static final class QuickResult {
        public final Action action;
        public final Draft draft; // datos del borrador (sin id aún)
        QuickResult(Action a, Draft d) { this.action = a; this.draft = d; }
        public static QuickResult of(Action a, Draft d){ return new QuickResult(a,d); }
    }

    /**
     * Diálogo “Nuevo borrador / Editar borrador simple”.
     * - Título opcional
     * - Área de texto
     * - [ ] Proteger con PIN
     * Botones: Guardar (como borrador), Convertir a píldora, Cancelar
     */
    public static QuickResult showQuickDraftDialog(Parent owner,
                                                   String initialTitle,
                                                   String initialBody,
                                                   boolean initialProtected) {
        var sec = SecurityService.getInstance();

        Dialog<QuickResult> d = new Dialog<>();
        d.setTitle("Borrador rápido");
        d.setHeaderText("Escribe una nota rápida");

        TextField tfTitulo = new TextField();
        tfTitulo.setPromptText("Título (opcional)");
        tfTitulo.setText(initialTitle == null ? "" : initialTitle);

        TextArea ta = new TextArea();
        ta.setPromptText("Contenido…");
        ta.setWrapText(true);
        ta.setPrefRowCount(10);
        ta.setText(initialBody == null ? "" : initialBody);

        CheckBox cbProtect = new CheckBox("Proteger con PIN");
        cbProtect.setSelected(initialProtected);

        VBox content = new VBox(8, new Label("Título:"), tfTitulo, new Label("Contenido:"), ta, cbProtect);
        content.setPadding(new Insets(8));
        d.getDialogPane().setContent(content);

        ButtonType btSave    = new ButtonType("Guardar", ButtonBar.ButtonData.APPLY);
        ButtonType btConvert = new ButtonType("Convertir a píldora", ButtonBar.ButtonData.OK_DONE);
        ButtonType btCancel  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().setAll(btSave, btConvert, btCancel);
        
     // Atajos: Ctrl+S y Ctrl+Enter -> Guardar (robusto en diálogos)
        d.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            boolean shortcut = e.isShortcutDown(); // Ctrl en Win/Linux, Cmd en macOS

            if (shortcut && e.getCode() == KeyCode.S) {
                e.consume();
                var node = d.getDialogPane().lookupButton(btSave);
                if (node instanceof Button b && !b.isDisabled()) {
                    b.fire();
                }
            }
            if (shortcut && e.getCode() == KeyCode.ENTER) {
                e.consume();
                var node = d.getDialogPane().lookupButton(btSave);
                if (node instanceof Button b && !b.isDisabled()) {
                    b.fire();
                }
            }
        });

        // Decora con tu tema
        Dialogs.decorate(d, owner);

        // foco directo al cuerpo (o al título, como prefieras)
        Platform.runLater(ta::requestFocus);

        // Validación mínima: si NO se protege, exige texto en contenido (como en píldoras)
        Node saveNode = d.getDialogPane().lookupButton(btSave);
        Node convertNode = d.getDialogPane().lookupButton(btConvert);
        Runnable validate = () -> {
            boolean okIfUnprotected = !cbProtect.isSelected()
                    ? !ta.getText().trim().isBlank()
                    : true; // si se protege, permitimos vacío (puede querer solo el título)
            saveNode.setDisable(!okIfUnprotected);
            convertNode.setDisable(ta.getText().trim().isBlank()); // para convertir, mejor exigir contenido
        };
        ta.textProperty().addListener((o,a,b)->validate.run());
        cbProtect.selectedProperty().addListener((o,a,b)->validate.run());
        validate.run();

        d.setResultConverter(bt -> {
            if (bt == btCancel) return null;

            boolean proteger = cbProtect.isSelected();
            String titulo = tfTitulo.getText().trim();
            String body   = ta.getText();

            byte[] cipher = null, iv = null;

            // Si quiere proteger, necesitamos asegurar PIN
            if (proteger) {
                if (!sec.hasPin()) {
                    StatusBus.show("Primero crea un PIN en Ajustes → Seguridad.", StatusBus.Type.WARN, Duration.seconds(4));
                    return null;
                }
                if (!sec.ensureUnlocked(() -> PinDialogs.promptPin6(owner, true, java.time.Duration.ofMinutes(10)))) {
                    // Si estaba en lockout, el propio ensureUnlocked ya saca el modal
                    return null;
                }
                var enc = sec.encrypt(body == null ? "" : body);
                cipher = enc.cipher; iv = enc.iv;
                body = null;
            }

            Draft dft = Draft.of(titulo.isBlank()? null : titulo, body, proteger, cipher, iv);

            if (bt == btSave)    return QuickResult.of(Action.SAVE, dft);
            if (bt == btConvert) return QuickResult.of(Action.CONVERT, dft);
            return null;
        });

        return d.showAndWait().orElse(null);
    }

    /**
     * Bandeja de borradores: tabla con Editar / Convertir / Eliminar.
     * onConvert: recibe el borrador (plano si no protegido, o cifrado si lo estaba). Si conviertes, normalmente eliminas el borrador.
     */
    @SuppressWarnings("unchecked")
    public static void showDraftsTray(Parent owner,
                                      Consumer<Draft> onEdit,
                                      Consumer<Draft> onConvert,
                                      Consumer<Draft> onDelete) {
        var dao = new DraftDao();
        var sec = SecurityService.getInstance();

        Dialog<Void> d = new Dialog<>();
        d.setTitle("Borradores");
        d.setHeaderText(null);

        // --- Tabla ---
        TableView<Draft> table = new TableView<>();

        TableColumn<Draft, String> cTit = new TableColumn<>("Título");
        cTit.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getTitulo() == null ? "" : cd.getValue().getTitulo()
        ));
        cTit.setPrefWidth(150);

        TableColumn<Draft, String> cProt = new TableColumn<>("Contenido");
        cProt.setCellValueFactory(cd -> new SimpleStringProperty(
        		cd.getValue().getContenido()
        		));
        cProt.setCellFactory(col -> new TableCell<Draft, String>() {
            private final FontIcon lock = new FontIcon(FontAwesomeSolid.LOCK);
            private final Label lbl = new Label();
            private final HBox box = new HBox(6);

            {
                lock.setIconSize(12);
                lock.getStyleClass().addAll("muted-icon", "danger");
                lbl.setWrapText(false);
                lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
                lbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lbl, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Draft dft = getTableRow().getItem();
                box.getChildren().clear();

                if (dft.isProtegida()) {
                    lbl.setText("Contenido protegido");
                    box.getChildren().addAll(lock, lbl);
                    setTooltip(null); // sin tooltip para protegidos (igual que en la tabla principal)
                } else {
                    // Preview en una línea (recorta largo)
                    String text = (item == null ? "" : item.replaceAll("\\s+", " ").trim());
                    if (text.length() > 160) text = text.substring(0, 160) + "…";
                    lbl.setText(text);
                    box.getChildren().add(lbl);
                    // si quieres tooltip con el contenido completo:
                    // setTooltip(text.isBlank() ? null : new Tooltip(item));
                }

                setGraphic(box);
                setText(null);
            }
        });
        cProt.setPrefWidth(250);

        table.getColumns().addAll(cTit, cProt);
        table.setItems(FXCollections.observableList(dao.listarTodos()));
        
     // --- Columna Acciones: botón Eliminar ---
        TableColumn<Draft, Void> cAcc = new TableColumn<>("");
        cAcc.setPrefWidth(30);
        cAcc.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(null));

        // Helper local: borrar con confirmación + refresco
        Consumer<Draft> doDelete = (dft) -> {
            if (dft == null) return;
            var conf = new Alert(Alert.AlertType.CONFIRMATION, "¿Eliminar este borrador?");
            Dialogs.decorate(conf, owner);
            conf.showAndWait().ifPresent(res -> {
                if (res.getButtonData().isDefaultButton()) {
                    // feedback externo si te pasaron un onDelete (sólo mensaje, etc.)
                    if (onDelete != null) onDelete.accept(dft);

                    // borra y refresca
                    dao.eliminar(dft.getId());
                    table.setItems(FXCollections.observableList(dao.listarTodos()));
                    // re-selección amable
                    if (!table.getItems().isEmpty()) {
                        int idx = Math.min(table.getSelectionModel().getSelectedIndex(), table.getItems().size() - 1);
                        table.getSelectionModel().select(Math.max(idx, 0));
                    }
                }
            });
        };
        

        cAcc.setCellFactory(col -> new TableCell<>() {
            final Button btnDel = new Button("Eliminar");
//            final Button btnDel = new Button(new FontIcon(FontAwesomeSolid.TRASH), "Eliminar (Supr)");
            final HBox box = new HBox(8, btnDel);
            {
            	box.setStyle("-fx-alignment: CENTER;");
            	btnDel.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
                btnDel.getStyleClass().addAll("icon-btn", "danger");
                btnDel.setOnAction(e -> {
                    Draft dft = getTableRow() == null ? null : getTableRow().getItem();
                    doDelete.accept(dft);
                });
                box.setPadding(new Insets(2, 0, 2, 0));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().add(cAcc);

        // --- Tecla Supr: borrar seleccionado ---
        table.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DELETE) {
                e.consume();
                Draft sel = table.getSelectionModel().getSelectedItem();
                doDelete.accept(sel);
            }
        });

     // Doble-clic: abrir editor rápido pre-relleno
        table.setRowFactory(tv -> {
            TableRow<Draft> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (row.isEmpty() || ev.getClickCount() != 2) return;

                Draft dft = row.getItem();
                String plain = dft.getContenido();

                // Si está protegido, intentamos desbloquear + descifrar antes de abrir el editor
                if (dft.isProtegida()) {
                    plain = tryDecryptForDialog(owner, sec, dft);
                    if (plain == null) {
                        // canceló PIN o lockout -> no abrimos editor
                        return;
                    }
                }

                // Editor de borradores (pre-relleno)
                var res = showQuickDraftDialog(
                    owner,
                    dft.getTitulo(),
                    plain,                    // cuerpo en claro (o texto), nunca cifrado
                    dft.isProtegida()         // pre-marca “Proteger con PIN”
                );
                if (res == null) return;

                switch (res.action) {
                    case SAVE -> {
                        // Actualiza el borrador existente con lo que venga del editor rápido
                        dft.setTitulo(res.draft.getTitulo());
                        dft.setProtegida(res.draft.isProtegida());
                        dft.setContenido(res.draft.getContenido());
                        dft.setContenidoCipher(res.draft.getContenidoCipher());
                        dft.setContenidoIv(res.draft.getContenidoIv());
                        new DraftDao().actualizar(dft);
                        table.setItems(FXCollections.observableList(dao.listarTodos()));
                        StatusBus.show("Borrador actualizado.", StatusBus.Type.SUCCESS, Duration.seconds(2));
                    }
                    case CONVERT -> {
                        // Delegas la conversión al caller (ListadopildorasController), si te interesa.
                        if (onConvert != null) onConvert.accept(res.draft);
                        // Por defecto, eliminamos el borrador tras convertir
                        new DraftDao().eliminar(dft.getId());
                        table.setItems(FXCollections.observableList(dao.listarTodos()));
                    }
                    default -> { /* CANCEL: nada */ }
                }
            });
            return row;
        });

        d.getDialogPane().setContent(new VBox(8, table));
        d.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));

        Dialogs.decorate(d, owner); // <- se encargará de poner la barra de título (con ✕ que cierra)
        d.showAndWait();
    }

    private static String tryDecryptForDialog(Parent owner, SecurityService sec, Draft dft) {
        try {
            if (dft == null || !dft.isProtegida()) {
                return (dft == null) ? "" : (dft.getContenido() == null ? "" : dft.getContenido());
            }
            // Si la sesión no está desbloqueada, pide PIN (con tu modal tematizado)
            if (!sec.isUnlocked()) {
                boolean ok = sec.ensureUnlocked(() -> PinDialogs.promptPin6(owner, true, java.time.Duration.ofMinutes(10)));
                if (!ok) return null; // canceló o lockout -> no abrir editor
            }
            return sec.decrypt(dft.getContenidoCipher(), dft.getContenidoIv());
        } catch (Exception ex) {
            // Datos corruptos o clave no válida
            StatusBus.show("No se pudo descifrar el borrador.", StatusBus.Type.ERROR, Duration.seconds(3));
            return null;
        }
    }

}
