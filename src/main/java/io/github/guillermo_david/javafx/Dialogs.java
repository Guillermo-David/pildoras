package io.github.guillermo_david.javafx;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class Dialogs {
	private Dialogs() {
	}

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
		alert.setHeaderText(null); // quitamos el header "por defecto"
		pane.setHeader(titleBar);

		// Copiar exactamente los estilos del Scene principal
		var mainSS = ownerRoot.getScene().getStylesheets();
		var dlgSS = dlgScene.getStylesheets();
		dlgSS.setAll(mainSS);

		// Botón por defecto (Enter)
		pane.getButtonTypes().forEach(bt -> {
			Button b = (Button) pane.lookupButton(bt);
			if (b != null && bt.getButtonData().isDefaultButton())
				b.setDefaultButton(true);
		});
	}

	public static <T> void decorate(Dialog<T> dialog, Parent ownerRoot) {
		// 1) estilo/owner antes de que se cree el Stage interno
		dialog.initStyle(StageStyle.UNDECORATED);
		dialog.initOwner(ownerRoot.getScene().getWindow());
		dialog.initModality(Modality.WINDOW_MODAL);

		DialogPane pane = dialog.getDialogPane();

		// 2) hereda todos los estilos del Scene principal
		pane.getStylesheets().setAll(ownerRoot.getScene().getStylesheets());
		// estilo identificable en CSS
		pane.getStyleClass().add("themed-dialog");

		// 3) titlebar custom idéntica a la de tu app
		HBox titleBar = buildTitleBar(dialog);
		pane.setHeader(titleBar);

		// 4) seguridad: si por algún motivo ya se creó el Stage con decoraciones,
		// abortamos el intento (no se puede cambiar el style después)
		pane.sceneProperty().addListener((obs, old, sc) -> {
			if (sc != null) {
				Stage st = (Stage) sc.getWindow();
				// si no está undecorated, cierra y re-abrimos manualmente (extremo; rara vez
				// ocurre)
				if (st.getStyle() != StageStyle.UNDECORATED) {
					throw new IllegalStateException(
							"El diálogo no está undecorated; asegúrate de llamar a Dialogs.decorate() antes de showAndWait().");
				}
			}
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
		bar.setOnMousePressed(e -> {
			off[0] = e.getScreenX() - dlgStage.getX();
			off[1] = e.getScreenY() - dlgStage.getY();
		});
		bar.setOnMouseDragged(e -> {
			dlgStage.setX(e.getScreenX() - off[0]);
			dlgStage.setY(e.getScreenY() - off[1]);
		});
		btnClose.setOnMousePressed(e -> e.consume());
		btnClose.setOnMouseDragged(e -> e.consume());

		return bar;
	}

	private static <T> HBox buildTitleBar(Dialog<T> dialog) {
		Label title = new Label(dialog.getTitle() == null ? "" : dialog.getTitle());
		title.getStyleClass().add("titlebar-title");

		Pane spacer = new Pane();
		HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

		Button close = new Button("✕");
		close.getStyleClass().add("titlebar-close");
		close.setOnAction(e -> dialog.setResult(null));

		HBox bar = new HBox(8, title, spacer, close);
		bar.getStyleClass().add("titlebar"); // usa tus estilos .titlebar

		final double[] delta = new double[2];
		bar.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
			Stage st = (Stage) dialog.getDialogPane().getScene().getWindow();
			delta[0] = ev.getSceneX();
			delta[1] = ev.getSceneY();
		});
		bar.addEventFilter(MouseEvent.MOUSE_DRAGGED, ev -> {
			Stage st = (Stage) dialog.getDialogPane().getScene().getWindow();
			st.setX(ev.getScreenX() - delta[0]);
			st.setY(ev.getScreenY() - delta[1]);
		});

		return bar;
	}
}
