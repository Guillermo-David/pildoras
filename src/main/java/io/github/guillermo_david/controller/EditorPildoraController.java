package io.github.guillermo_david.controller;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;

import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.ColorUtil;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.StatusBus.Type;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import javafx.application.Platform;
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
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.Duration;

public class EditorPildoraController {

	@FXML private TextField txtTagInput, txtTitulo;
	@FXML private TextArea txtDescripcion;
	@FXML private ToggleSwitch switchProteger;
	@FXML private Button btnGuardar, btnCancelar;
	@FXML private FlowPane tagsBox;
	@FXML private VBox root;


	private Runnable onClose;
	private Pildora pildoraEnEdicion;

	private final PildoraDao pildoraDao = new PildoraDao();
	private final TagDao tagDao = new TagDao();
	private final PildoraTagDao pildoraTagDao = new PildoraTagDao();

	private AutoCompletionBinding<String> tagAuto;
	private EventHandler<KeyEvent> editorKeyHandler;
	private final Set<String> currentTags = new LinkedHashSet<>();

	private final SecurityService security = SecurityService.getInstance();
	private boolean originalProtegida = false;

	@FXML
	public void initialize() {
		btnCancelar.setOnAction(e -> {
			if (onClose != null)
				onClose.run();
		});

		btnGuardar.setOnAction(e -> {
			onGuardar();
		});

		// (Opcional) Tooltips con pistas
		btnGuardar.setTooltip(new Tooltip("Guardar (Ctrl+S)"));
		btnCancelar.setTooltip(new Tooltip("Cancelar (Esc)"));

		// Autocompletado
		tagAuto = TextFields.bindAutoCompletion(txtTagInput,
				(Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>>) req -> {
					String q = req.getUserText();
					if (q == null || q.isBlank())
						return List.of();
					return tagDao.listAllLike(q.toLowerCase(), 20).stream().map(String::toLowerCase).toList();
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
				for (String t : currentTags)
					last = t;
				if (last != null) {
					currentTags.remove(last);
					int n = tagsBox.getChildren().size();
					if (n > 0)
						tagsBox.getChildren().remove(n - 1);
					e.consume();
				}
			}
		});

		tagsBox.sceneProperty().addListener((obs, oldScene, scene) -> {
			if (scene == null)
				return;
			scene.getStylesheets().addListener(new ListChangeListener<String>() {
				@Override
				public void onChanged(Change<? extends String> c) {
					recolorChips();
				}
			});
		});

		if (switchProteger != null) {
			switchProteger.setTooltip(new Tooltip("Proteger con PIN (cifra el contenido)"));

			switchProteger.selectedProperty().addListener((obs, wasSelected, nowSelected) -> {
				if (pildoraEnEdicion == null)
					return;

				// No permitir DESPROTEGER si la píldora venía protegida y seguimos bloqueados
				if (originalProtegida && !security.isUnlocked() && !nowSelected) {
					// Revertimos el toggle sin tocar el TextArea
					Platform.runLater(() -> switchProteger.setSelected(true));
					StatusBus.show("Desbloquea con PIN para desproteger.", StatusBus.Type.WARN, Duration.seconds(3));
					return;
				}

				// Solo feedback, sin tocar txtDescripcion
				if (!wasSelected && nowSelected && !originalProtegida) {
					StatusBus.show("Se protegerá al guardar.", StatusBus.Type.INFO, Duration.seconds(2));
				} else if (wasSelected && !nowSelected && originalProtegida && security.isUnlocked()) {
					StatusBus.show("Se desprotegerá al guardar.", StatusBus.Type.INFO, Duration.seconds(2));
				}
			});
		}

	}

	private void onGuardar() {
		String titulo = txtTitulo.getText().trim();
		boolean proteger = switchProteger.isSelected();

		if (titulo.isBlank()) {
			StatusBus.show("El título es obligatorio.", Type.WARN, Duration.seconds(4));
			return;
		}

		// Si venía protegida y sigues bloqueado → exige PIN antes de cualquier guardado
		if (originalProtegida && !security.isUnlocked()) {
			if (originalProtegida && !security.isUnlocked()) {
			    boolean ok = security.ensureUnlocked(
			        () -> PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10))
			        // alternativa sin fx:id root:
			        // () -> PinDialogs.promptPin6(txtTitulo.getScene().getRoot(), true, Duration.ofMinutes(10))
			    );
			    if (!ok) {
			        StatusBus.show("No puedes guardar ni desproteger sin PIN.", StatusBus.Type.ERROR, Duration.seconds(4));
			        return;
			    }
			    // Si viene protegida y acabas de desbloquear, rellena el editor con el contenido real
			    try {
			        if (pildoraEnEdicion != null &&
			            pildoraEnEdicion.getDescripcionCipher() != null &&
			            pildoraEnEdicion.getDescripcionIv() != null) {
			            String plain = security.decrypt(
			                pildoraEnEdicion.getDescripcionCipher(),
			                pildoraEnEdicion.getDescripcionIv()
			            );
			            txtDescripcion.setDisable(false);
			            if (txtDescripcion.getText().isBlank()) {
			                txtDescripcion.setText(plain);
			            }
			        }
			    } catch (Exception ignored) {}
			}
			// Si viene protegida y acabas de desbloquear, rellena el editor con el
			// contenido real
			try {
				if (pildoraEnEdicion != null && pildoraEnEdicion.getDescripcionCipher() != null
						&& pildoraEnEdicion.getDescripcionIv() != null) {
					String plain = security.decrypt(pildoraEnEdicion.getDescripcionCipher(),
							pildoraEnEdicion.getDescripcionIv());
					txtDescripcion.setDisable(false);
					if (txtDescripcion.getText().isBlank()) {
						txtDescripcion.setText(plain);
					}
				}
			} catch (Exception ignored) {
			}
		}

		String descripcionPlano = txtDescripcion.getText();

		if (!proteger && (descripcionPlano == null || descripcionPlano.trim().isBlank())) {
			StatusBus.show("La descripción es obligatoria si no proteges la píldora.", Type.WARN, Duration.seconds(4));
			return;
		}

		try {
			byte[] cipher = null, iv = null;

			if (proteger) {
				// Si no hay PIN aún, configúralo
				if (!security.hasPin()) {
					char[] pin = askNewPin6();
					if (pin == null) {
						StatusBus.show("Protección cancelada.", StatusBus.Type.WARN, Duration.seconds(3));
						return;
					}
					try {
						security.setupPin(pin);
					} finally {
						java.util.Arrays.fill(pin, '\0');
					}
				}
				// Garantiza desbloqueo con feedback consistente (status bar + segundos restantes)
				if (!ensureUnlockedForEditor()) {
				    return;
				}

				var enc = security.encrypt(descripcionPlano == null ? "" : descripcionPlano);
				cipher = enc.cipher;
				iv = enc.iv;
				descripcionPlano = null; // BD: NULL cuando está protegida
			}

			// --- Guardado en BD ---
			if (pildoraEnEdicion == null) {
				Pildora nueva = new Pildora(titulo, descripcionPlano, proteger, cipher, iv);
				pildoraDao.insertar(nueva);
				persistTags(nueva.getId());
			} else {
				pildoraEnEdicion.setTitulo(titulo);
				pildoraEnEdicion.setProtegida(proteger);
				pildoraEnEdicion.setDescripcion(descripcionPlano);
				pildoraEnEdicion.setDescripcionCipher(cipher);
				pildoraEnEdicion.setDescripcionIv(iv);

				pildoraDao.actualizar(pildoraEnEdicion);
				persistTags(pildoraEnEdicion.getId());
			}

			// --- Mensaje único + re-bloqueo si procede ---
			boolean becameProtected = (pildoraEnEdicion == null && switchProteger.isSelected())
					|| (pildoraEnEdicion != null && !originalProtegida && switchProteger.isSelected());

			String msg = "Píldora guardada correctamente.";
			if (becameProtected) {
				io.github.guillermo_david.security.SecurityService.getInstance().lockNow();
				msg = "Píldora guardada y protegida. Se pedirá PIN al abrirla.";
			}

			StatusBus.show(msg, StatusBus.Type.SUCCESS, Duration.seconds(3));

			if (onClose != null)
				onClose.run();

		} catch (Exception ex) {
			ex.printStackTrace();
			StatusBus.show("Error al guardar: " + ex.getMessage(), Type.ERROR, Duration.seconds(4));
		}
	}

	private char[] askPin6() {
		var dlg = new javafx.scene.control.TextInputDialog();
		dlg.setTitle("PIN requerido");
		dlg.setHeaderText("Introduce tu PIN (6 dígitos)");
		dlg.setContentText("PIN:");
		var tf = dlg.getEditor();
		tf.setPromptText("••••••");
		tf.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c -> {
			String t = c.getControlNewText();
			return t.matches("\\d{0,6}") ? c : null;
		}));
		var res = dlg.showAndWait();
		if (res.isEmpty() || res.get().length() != 6)
			return null;
		return res.get().toCharArray();
	}

	private char[] askNewPin6() {
		// muy simple: dos diálogos seguidos
		var p1 = askPin6();
		if (p1 == null)
			return null;
		var p2 = askPin6();
		if (p2 == null) {
			java.util.Arrays.fill(p1, '\0');
			return null;
		}
		boolean eq = java.util.Arrays.equals(p1, p2);
		java.util.Arrays.fill(p2, '\0');
		if (!eq) {
			StatusBus.show("Los PIN no coinciden.", Type.WARN, Duration.seconds(3));
			java.util.Arrays.fill(p1, '\0');
			return null;
		}
		return p1;
	}

	public void cargarPildora(Pildora p) {
		this.pildoraEnEdicion = p;
		txtTitulo.setText(p.getTitulo());
		if (switchProteger != null)
			switchProteger.setSelected(p.isProtegida());

		if (p.isProtegida()) {
			if (security.isUnlocked()) {
				try {
					String plano = security.decrypt(p.getDescripcionCipher(), p.getDescripcionIv());
					txtDescripcion.setDisable(false);
					txtDescripcion.setText(plano);
				} catch (Exception e) {
					txtDescripcion.clear();
					txtDescripcion.setDisable(true);
					txtDescripcion.setPromptText("Contenido protegido (error de descifrado)");
				}
			} else {
				// venía bloqueado -> no abrir nunca el contenido aquí
				txtDescripcion.clear();
				txtDescripcion.setDisable(true);
				txtDescripcion.setPromptText("Contenido protegido — desbloquea desde el listado");
			}
		} else {
			txtDescripcion.setDisable(false);
			txtDescripcion.setText(p.getDescripcion() == null ? "" : p.getDescripcion());
		}

		currentTags.clear();
		tagsBox.getChildren().clear();
		tagDao.findByPildoraId(p.getId()).forEach(t -> addTagChip(t.getNombre()));
	}

	/** Muestra un modal con el tiempo restante del lockout (tema aplicado). */
	private void showLockoutModal() {
	    
	    long secs = Math.max(0L, (security.lockoutRemainingMillis() + 999) / 1000);

	    Alert a = new Alert(Alert.AlertType.WARNING);
	    a.setTitle("Intentos agotados");
	    a.setHeaderText("Has excedido los intentos del PIN");
	    a.setContentText("Podrás volver a intentarlo en " + secs + " segundo" + (secs == 1 ? "" : "s") + ".");
	    Dialogs.decorate(a, root); // 👈 mantiene el tema/estilo de la app
	    a.showAndWait();
	}

	/** Desbloqueo con reintentos y modal si está bloqueado. */
	private boolean ensureUnlockedForEditor() {

	    // Ya desbloqueado o recordado
	    if (security.isUnlocked() || security.isRemembered()) return true;

	    // Bloqueado: mostrar MODAL con segundos restantes
	    if (security.isLockedOut()) {
	        showLockoutModal();
	        return false;
	    }

	    // Reintentos (máximo configurado como en listados: 3)
	    final int MAX = 3;
	    int attempts = 0;

	    while (attempts < MAX) {
	        char[] pin = PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10));
	        if (pin == null) return false; // cancelado

	        try {
	            if (security.verifyPin(pin)) {
	                StatusBus.show("Desbloqueado por 10 minutos.", StatusBus.Type.INFO, Duration.seconds(3));
	                return true;
	            } else {
	                attempts++;
	                // Si justo aquí entra en lockout, mostrar MODAL (no status bar)
	                if (security.isLockedOut()) {
	                    showLockoutModal();
	                    return false;
	                }
	                int left = MAX - attempts;
	                StatusBus.show(
	                    "PIN incorrecto. Te quedan " + left + " intento" + (left == 1 ? "" : "s") + ".",
	                    StatusBus.Type.WARN,
	                    Duration.seconds(3)
	                );
	            }
	        } finally {
	            Arrays.fill(pin, '\0');
	        }
	    }
	    return false;
	}


	private void commitTagFromInput() {
		String raw = txtTagInput.getText();
		if (raw == null)
			return;

		for (String part : raw.split(",")) {
			String t = part.trim().replaceAll("\\s+", " ").toLowerCase();
			if (!t.isEmpty())
				addTagChip(t);
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

	private void recolorChips() {
		boolean dark = (ThemeManager.load() == ThemeManager.Theme.DARK);
		tagsBox.getChildren().forEach(n -> {
			if (n instanceof HBox box && !box.getChildren().isEmpty()
					&& box.getChildren().get(0) instanceof Label lbl) {
				String name = lbl.getText();
				String bg = ColorUtil.colorForTag(name, dark);
				String fg = ColorUtil.bestTextOn(bg);
				box.setStyle(String.format("-fx-tag-bg: %s; -fx-tag-fg: %s;", bg, fg));
			}
		});
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

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}
}
