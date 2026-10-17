package io.github.guillermo_david.controller;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.util.I18n;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class TrashDialog {

    private TrashDialog() {}

    static void show(Parent owner, Runnable onChanged) {
        var dao = new PildoraDao();
        var items = FXCollections.observableArrayList(dao.listarPapelera());
        var list = new ListView<PildoraDao.TrashItem>(items);
        list.setPrefSize(460, 300);
        list.setPlaceholder(new Label(I18n.get("trash.empty")));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PildoraDao.TrashItem t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null
                        : t.titulo() + (t.fechaEliminacion() == null ? "" : "  (" + t.fechaEliminacion() + ")"));
            }
        });

        var btnRestore = new Button(I18n.get("trash.restore"));
        var btnDelete = new Button(I18n.get("trash.delete.permanent"));
        var btnEmpty = new Button(I18n.get("trash.empty.all"));
        btnRestore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        btnEmpty.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(items));

        btnRestore.setOnAction(e -> {
            var t = list.getSelectionModel().getSelectedItem();
            dao.restaurar(t.id());
            items.remove(t);
            onChanged.run();
            StatusBus.show(I18n.get("trash.restored"), StatusBus.Type.SUCCESS, Duration.seconds(3));
        });
        btnDelete.setOnAction(e -> {
            var t = list.getSelectionModel().getSelectedItem();
            if (confirm(owner, I18n.get("trash.confirm.delete", t.titulo()))) {
                dao.eliminarDefinitivo(t.id());
                items.remove(t);
                onChanged.run();
            }
        });
        btnEmpty.setOnAction(e -> {
            if (confirm(owner, I18n.get("trash.confirm.empty", items.size()))) {
                dao.vaciarPapelera();
                items.clear();
                onChanged.run();
            }
        });

        var dlg = new Dialog<Void>();
        dlg.setTitle(I18n.get("trash.title"));
        var box = new VBox(10, list, new HBox(8, btnRestore, btnDelete, btnEmpty));
        box.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Dialogs.decorate(dlg, owner);
        dlg.showAndWait();
    }

    private static boolean confirm(Parent owner, String text) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, text);
        Dialogs.decorate(a, owner);
        return a.showAndWait().map(b -> b.getButtonData().isDefaultButton()).orElse(false);
    }
}
