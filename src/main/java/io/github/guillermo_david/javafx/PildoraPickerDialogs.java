package io.github.guillermo_david.javafx;

import java.util.List;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.model.Pildora;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class PildoraPickerDialogs {
    private PildoraPickerDialogs(){}

    @SuppressWarnings("unchecked")
    public static Pildora show(Parent owner) {
        Dialog<Pildora> d = new Dialog<>();
        d.setTitle("Elegir píldora");
        d.setHeaderText(null);

        TextField tfFilter = new TextField();
        tfFilter.setPromptText("Filtrar por título…");

        TableView<Pildora> table = new TableView<>();
        table.setFocusTraversable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Pildora, Number> cId = new TableColumn<>("#");
        cId.setMinWidth(60);
        cId.setMaxWidth(80);
        cId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getId()));

        TableColumn<Pildora, String> cTitulo = new TableColumn<>("Título");
        cTitulo.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getTitulo() == null ? "" : cd.getValue().getTitulo()
        ));

        table.getColumns().setAll(cId, cTitulo);

        ObservableList<Pildora> data = FXCollections.observableArrayList();
        table.setItems(data);

        VBox content = new VBox(8, tfFilter, table);
        content.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        d.getDialogPane().setContent(content);

        ButtonType btOk = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().setAll(btOk, btCancel);

        // Tema
        Dialogs.decorate(d, owner);

        // Buscar (con debounce)
        PildoraDao dao = new PildoraDao();
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        Runnable doSearch = () -> {
            String q = tfFilter.getText() == null ? "" : tfFilter.getText().trim();
            List<Pildora> rows = dao.buscarPorTitulo(q, 100);
            data.setAll(rows);
            if (!rows.isEmpty()) table.getSelectionModel().selectFirst();
        };
        debounce.setOnFinished(e -> doSearch.run());
        tfFilter.textProperty().addListener((o, a, b) -> debounce.playFromStart());

        // Doble clic/Enter en tabla = aceptar
        table.setRowFactory(tv -> {
            TableRow<Pildora> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    d.setResult(row.getItem());
                    d.close();
                }
            });
            return row;
        });
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Pildora sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    d.setResult(sel);
                    d.close();
                }
            }
        });

        // Deshabilita OK si no hay selección
        d.getDialogPane().lookupButton(btOk).disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull()
        );

        d.setResultConverter(bt -> (bt == btOk)
                ? table.getSelectionModel().getSelectedItem()
                : null);

        // Primera carga + foco
        Platform.runLater(() -> {
            tfFilter.requestFocus();
            doSearch.run();
        });

        return d.showAndWait().orElse(null);
    }
}
