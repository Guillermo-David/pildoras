package io.github.guillermo_david.javafx;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;

import io.github.guillermo_david.security.SecurityService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class PinDialogs {
	private PinDialogs() {
	}

	/** Pide un nuevo PIN (6 dígitos) dos veces y valida igualdad. */
	public static char[] promptNewPin6(Parent owner) {
		char[] p1 = promptPin6(owner, false, java.time.Duration.ZERO, "Nuevo PIN",
				"Introduce tu nuevo PIN (6 dígitos)");
		if (p1 == null)
			return null;
		char[] p2 = promptPin6(owner, false, java.time.Duration.ZERO, "Confirmar PIN", "Repite el PIN");
		if (p2 == null) {
			java.util.Arrays.fill(p1, '\0');
			return null;
		}
		boolean eq = java.util.Arrays.equals(p1, p2);
		Arrays.fill(p2, '\0');
		if (!eq) {
			showWarn(owner, "Los PIN no coinciden. Vuelve a intentarlo.");
			Arrays.fill(p1, '\0');
			return null;
		}
		return p1;
	}

	/**
	 * Configura o actualiza la pregunta de seguridad. Devuelve [pregunta,
	 * respuesta] o null.
	 */
	public static AbstractMap.SimpleEntry<String, char[]> promptSetSecurityQuestion(Parent owner,
			String initialQuestion) {
		Dialog<AbstractMap.SimpleEntry<String, char[]>> d = new Dialog<>();
		d.setTitle("Pregunta de seguridad");
		d.setHeaderText("Configura una pregunta y su respuesta (se usará para recuperar el PIN)");

		TextField tfQ = new TextField();
		tfQ.setPromptText("Ej: Nombre de tu primera mascota");
		if (initialQuestion != null)
			tfQ.setText(initialQuestion);

		PasswordField pfA = new PasswordField();
		pfA.setPromptText("Respuesta (se recomienda algo memorable)");

		VBox box = new VBox(8, new Label("Pregunta:"), tfQ, new Label("Respuesta:"), pfA);
		box.setPadding(new Insets(4));
		d.getDialogPane().setContent(box);

		ButtonType ok = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().setAll(ok, cancel);

		// deshabilita OK si falta algo
		var okNode = d.getDialogPane().lookupButton(ok);
		okNode.setDisable(true);
		tfQ.textProperty()
				.addListener((o, a, b) -> okNode.setDisable(b == null || b.isBlank() || pfA.getText().isBlank()));
		pfA.textProperty()
				.addListener((o, a, b) -> okNode.setDisable(b == null || b.isBlank() || tfQ.getText().isBlank()));

		Dialogs.decorate(d, owner);
		Platform.runLater(tfQ::requestFocus);

		d.setResultConverter(bt -> {
			if (bt == ok) {
				return new AbstractMap.SimpleEntry<>(tfQ.getText().trim(), pfA.getText().toCharArray());
			}
			return null;
		});

		var res = d.showAndWait();
		return res.orElse(null);
	}

	public static char[] promptAnswer(Parent owner, String question) {
	    Dialog<char[]> d = new Dialog<>();
	    d.setTitle("Restablecer PIN");           // el header custom mostrará el título
	    d.setHeaderText(null);                   // no usamos headerText (lo oculta tu decorate)

	    // Pregunta (visible en el contenido)
	    Label lblQ = new Label(
	        (question != null && !question.isBlank())
	            ? question
	            : "Escribe la respuesta de seguridad"
	    );
	    lblQ.setWrapText(true);
	    lblQ.setMaxWidth(480); // por si la pregunta es larga

	    // Respuesta en claro (texto visible)
	    TextField tf = new TextField();
//	    tf.setPromptText("Respuesta (texto visible)");

	    VBox box = new VBox(10,
//	        new Label("Pregunta:"),
	        lblQ,
	        new Separator(),
//	        new Label("Respuesta:"),
	        tf
	    );
	    box.setPadding(new Insets(6, 0, 0, 0));
	    d.getDialogPane().setContent(box);

	    ButtonType ok     = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
	    ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
	    d.getDialogPane().getButtonTypes().setAll(ok, cancel);

	    // Deshabilita OK si está vacío
	    Node okNode = d.getDialogPane().lookupButton(ok);
	    okNode.setDisable(true);
	    tf.textProperty().addListener((o, a, b) -> okNode.setDisable(b == null || b.isBlank()));

	    // Aplica tu tema/estilos y foco
	    Dialogs.decorate(d, owner);
	    Platform.runLater(tf::requestFocus);

	    d.setResultConverter(bt -> (bt == ok) ? tf.getText().toCharArray() : null);
	    return d.showAndWait().orElse(null);
	}


	public static String promptRecoveryCode(Parent owner) {
		TextInputDialog d = new TextInputDialog();
		d.setTitle("Código de recuperación");
		d.setHeaderText("Introduce el código de recuperación (p.ej. ABCD-EFGH-IJKL-MNOP)");
		d.getEditor().setPromptText("XXXX-XXXX-XXXX-XXXX");
		Dialogs.decorate(d, owner);
		Platform.runLater(() -> d.getEditor().requestFocus());
		var r = d.showAndWait();
		return r.orElse(null);
	}
	
	private static void showLockoutModal(Parent owner) {
        var sec = SecurityService.getInstance();
        long secs = Math.max(0L, (sec.lockoutRemainingMillis()+999)/1000);
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Intentos agotados");
        a.setHeaderText("Has excedido los intentos del PIN");
        a.setContentText("Podrás volver a intentarlo en " + secs + " segundo" + (secs==1?"":"s") + ".");
        Dialogs.decorate(a, owner);
        a.showAndWait();
    }
    private static void showInfo(Parent owner, String msg)  { show(Alert.AlertType.INFORMATION, "Información", msg, owner); }
    private static void showWarn(Parent owner, String msg)  { show(Alert.AlertType.WARNING,    "Aviso",        msg, owner); }
    private static void showError(Parent owner, String msg) { show(Alert.AlertType.ERROR,      "Error",        msg, owner); }
    private static void show(Alert.AlertType t, String title, String msg, Parent owner) {
        Alert a = new Alert(t);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        Dialogs.decorate(a, owner);
        a.showAndWait();
    }

	/** Muestra el código generado con botón de copiar. */
	public static void showRecoveryCode(Parent owner, String code) {
		Dialog<Void> d = new Dialog<>();
		d.setTitle("Código de recuperación");
		d.setHeaderText("Copia y guarda este código en un lugar seguro");

		Label lbl = new Label(code);
		lbl.setStyle("-fx-font-family: Consolas, 'Fira Mono', monospace; -fx-font-size: 18px;");

		Button btnCopy = new Button("Copiar");
		btnCopy.setOnAction(e -> {
			ClipboardContent cc = new ClipboardContent();
			cc.putString(code);
			Clipboard.getSystemClipboard().setContent(cc);
		});

		VBox box = new VBox(10, lbl, btnCopy, new Label("Este código es de un solo uso."));
		box.setPadding(new Insets(8));
		d.getDialogPane().setContent(box);

		d.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
		Dialogs.decorate(d, owner);
		d.showAndWait();
	}

	public static char[] promptPin6(Parent owner, boolean showRemember, Duration rememberFor, String title,
			String header) {
		var dialog = new Dialog<char[]>();
		dialog.setTitle(title != null ? title : "PIN requerido");
		dialog.setHeaderText(header != null ? header : "Introduce tu PIN (6 dígitos)");

		var pf = new PasswordField();
		pf.setPromptText("••••••");
		pf.setTextFormatter(new TextFormatter<String>(c -> {
			String t = c.getControlNewText();
			return t.matches("\\d{0,6}") ? c : null;
		}));

		Label hint = new Label(); // mensajes contextuales (post-recuperación, etc.)
		Hyperlink forgot = new Hyperlink("¿Olvidaste tu PIN?");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox bottom;
		CheckBox remember = null;
		if (showRemember) {
			remember = new CheckBox("Recordar durante " + rememberFor.toMinutes() + " minutos");
			bottom = new HBox(12, remember, spacer, forgot);
		} else {
			bottom = new HBox(12, spacer, forgot);
		}

		VBox content = new VBox(8, new Label("PIN:"), pf, hint, bottom);
		content.setPadding(new Insets(4));
		dialog.getDialogPane().setContent(content);

		ButtonType okBtn = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().setAll(okBtn, cancel);

		Node okNode = dialog.getDialogPane().lookupButton(okBtn);
		okNode.setDisable(true);
		pf.textProperty().addListener((o, a, b) -> okNode.setDisable(b == null || b.length() != 6));

// Link de recuperación
		Label finalHint = hint;
		CheckBox finalRemember = remember;
		forgot.setOnAction(ev -> openRecoveryHub(owner, pf, finalHint));

// Theming + foco
		Dialogs.decorate(dialog, owner);
		Platform.runLater(pf::requestFocus);

		dialog.setResultConverter(bt -> {
			if (bt == okBtn) {
				if (showRemember && finalRemember != null && finalRemember.isSelected()) {
					SecurityService.getInstance().startRememberWindow(rememberFor);
				}
				return pf.getText().toCharArray();
			}
			return null;
		});

		return dialog.showAndWait().orElse(null);
	}

// === Hub de recuperación: elige método y ejecuta el flujo ===
	private static void openRecoveryHub(Parent owner, PasswordField pf, Label hint) {
		var sec = SecurityService.getInstance();
		if (sec.isLockedOut()) {
			showLockoutModal(owner);
			return;
		}

		Dialog<String> hub = new Dialog<>();
		hub.setTitle("Recuperación de PIN");
		hub.setHeaderText("Elige un método de recuperación");
		ButtonType byAnswer = new ButtonType("Con respuesta de seguridad", ButtonBar.ButtonData.OK_DONE);
		ButtonType byCode = new ButtonType("Con código de recuperación", ButtonBar.ButtonData.OTHER);
		ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		hub.getDialogPane().getButtonTypes().setAll(byAnswer, byCode, cancel);
		Dialogs.decorate(hub, owner);
		hub.setResultConverter(bt -> bt == byAnswer ? "answer" : bt == byCode ? "code" : null);
		String choice = hub.showAndWait().orElse(null);
		if (choice == null)
			return;

		boolean ok = false;
		try {
			switch (choice) {
			case "answer" -> {
				if (!sec.hasSecurityQuestion()) {
					showWarn(owner, "No hay pregunta de seguridad configurada.");
					return;
				}
				char[] ans = promptAnswer(owner, sec.getSecurityQuestion());
				if (ans == null)
					return;
				char[] np = promptNewPin6(owner);
				if (np == null) {
					Arrays.fill(ans, '\0');
					return;
				}
				try {
					ok = sec.resetPinWithAnswer(ans, np);
				} finally {
					Arrays.fill(ans, '\0');
					Arrays.fill(np, '\0');
				}
			}
			case "code" -> {
				if (!sec.hasRecoveryCode()) {
					showWarn(owner, "No hay código de recuperación activo.");
					return;
				}
				String code = promptRecoveryCode(owner);
				if (code == null || code.isBlank())
					return;
				char[] np = promptNewPin6(owner);
				if (np == null)
					return;
				try {
					ok = sec.resetPinWithRecoveryCode(code, np);
				} finally {
					Arrays.fill(np, '\0');
				}
			}
			}
		} catch (Exception ex) {
			ok = false;
		}

		if (ok) {
			showInfo(owner, "PIN restablecido.\nIntroduce el nuevo PIN para continuar.");
			pf.clear();
			hint.setText("PIN restablecido: escribe el nuevo PIN para continuar");
			Platform.runLater(pf::requestFocus);
		} else if (sec.isLockedOut()) {
			showLockoutModal(owner);
		} else {
			showError(owner, "No se pudo restablecer el PIN.");
		}
	}

	public static char[] promptPin6(Parent owner, boolean showRemember, Duration rememberFor) {
		return promptPin6(owner, showRemember, rememberFor, "PIN requerido", "Introduce tu PIN (6 dígitos)");
	}
}