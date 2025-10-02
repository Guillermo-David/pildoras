package io.github.guillermo_david.javafx;

import java.time.Duration;
import java.util.Optional;

import io.github.guillermo_david.security.SecurityService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

public final class PinDialogs {
	private PinDialogs() {
	}

	public static char[] promptPin6(Parent ownerRoot, boolean allowRemember, Duration rememberDuration) {
		Dialog<char[]> dialog = new Dialog<>();
		dialog.setTitle("PIN requerido");
		dialog.setHeaderText("Introduce tu PIN (6 dígitos)");

		PasswordField pf = new PasswordField();
		pf.setPromptText("••••••");
		pf.setTextFormatter(new TextFormatter<String>(c -> {
			String t = c.getControlNewText();
			return t.matches("\\d{0,6}") ? c : null;
		}));

		CheckBox remember = new CheckBox("Recordar durante " + rememberDuration.toMinutes() + " minutos");
		remember.setVisible(allowRemember);

		VBox content = new VBox(8, new Label("PIN:"), pf, remember);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		ButtonType okBtn = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelBtn = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().setAll(okBtn, cancelBtn);

		// deshabilitar Aceptar hasta tener 6 dígitos
		var okNode = dialog.getDialogPane().lookupButton(okBtn);
		okNode.setDisable(true);
		pf.textProperty().addListener((o, a, b) -> okNode.setDisable(b == null || b.length() != 6));

		// DECORAR (tema + titlebar) ANTES de mostrar
		Dialogs.decorate(dialog, ownerRoot);

		// foco 100% seguro
		dialog.setOnShown(e -> {
			pf.requestFocus();
			Platform.runLater(pf::requestFocus);
		});

		dialog.setResultConverter(bt -> {
			if (bt == okBtn) {
				if (allowRemember && remember.isSelected()) {
					SecurityService.getInstance().startRememberWindow(rememberDuration);
				}
				return pf.getText().toCharArray();
			}
			return null;
		});

		Optional<char[]> res = dialog.showAndWait();
		return res.orElse(null);
	}
}