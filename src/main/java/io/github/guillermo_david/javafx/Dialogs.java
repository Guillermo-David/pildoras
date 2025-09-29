package io.github.guillermo_david.javafx;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class Dialogs {
    private Dialogs() {}

    public static void decorate(Alert alert, Parent ownerRoot) {
        var pane = alert.getDialogPane();

        // Asegurar que el DialogPane tiene Scene antes de tocar el Stage
        pane.applyCss();
        Scene dlgScene = pane.getScene();
        Stage dlgStage = (Stage) dlgScene.getWindow();

        // Owner (iconos + modality + stylesheets)
        Stage ownerStage = (Stage) ownerRoot.getScene().getWindow();
        alert.initOwner(ownerStage);

        // Sin decoraciones del sistema
        dlgStage.initStyle(StageStyle.UNDECORATED);
        dlgStage.getIcons().setAll(ownerStage.getIcons());

        // Header custom
        var titleBar = buildDialogTitleBar(alert.getTitle(), dlgStage);
        alert.setHeaderText(null);              // quitamos el header "por defecto"
        pane.setHeader(titleBar);

        // Copiar exactamente los estilos del Scene principal
        var mainSS = ownerRoot.getScene().getStylesheets();
        var dlgSS  = dlgScene.getStylesheets();
        dlgSS.setAll(mainSS);

        // Botón por defecto (Enter)
        pane.getButtonTypes().forEach(bt -> {
            Button b = (Button) pane.lookupButton(bt);
            if (b != null && bt.getButtonData().isDefaultButton()) b.setDefaultButton(true);
        });
    }

    private static HBox buildDialogTitleBar(String title, Stage dlgStage) {
        Label lbl = new Label(title == null ? "" : title);
        lbl.getStyleClass().add("dialog-title");

        Button btnClose = new Button("✕");
        btnClose.getStyleClass().add("dialog-close");
        btnClose.setOnAction(e -> dlgStage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, lbl, spacer, btnClose);
        bar.getStyleClass().add("dialog-titlebar");

        // Drag mover
        final double[] off = new double[2];
        bar.setOnMousePressed(e -> { off[0]=e.getScreenX()-dlgStage.getX(); off[1]=e.getScreenY()-dlgStage.getY(); });
        bar.setOnMouseDragged(e -> { dlgStage.setX(e.getScreenX()-off[0]); dlgStage.setY(e.getScreenY()-off[1]); });
        btnClose.setOnMousePressed(e -> e.consume());
        btnClose.setOnMouseDragged(e -> e.consume());

        return bar;
    }
}
