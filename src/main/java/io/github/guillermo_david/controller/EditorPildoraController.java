package io.github.guillermo_david.controller;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;

import io.github.guillermo_david.util.I18n;
import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.PildoraLinkDao;
import io.github.guillermo_david.dao.PildoraTagDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.ColorUtil;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.StatusBus.Type;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.markdown.MarkdownEngine;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.Duration;

public class EditorPildoraController {

	@FXML private TextField txtTagInput, txtTitulo;
	@FXML private TextArea txtDescripcion;
	@FXML private ToggleSwitch switchProteger, switchPreview, switchOcultar;
	@FXML private Button btnGuardar, btnCancelar;
	@FXML private FlowPane tagsBox;
	@FXML private VBox root;
	@FXML private SplitPane splitEditor;
	@FXML private WebView webPreview;
	@FXML private MenuButton mbMath, mbHeadings, mbLists, mbBlocks, mbInsert, mbAdvanced;
	@FXML private javafx.scene.control.SplitMenuButton smbLinks;
	@FXML private javafx.scene.control.Label lblTags, lblTitle, lblContent;

	private TextFormatController textFormatController;

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
	private String decryptedContentCache = null;

	private MarkdownEngine md = new MarkdownEngine();
	private String cssLight, cssDark, cssBaseWeb;

	private static final Pattern P_PILDORA_ID = Pattern.compile("\\b[pP]ildora:(\\d+)");

	private static final java.util.prefs.Preferences RECOVERY_PREFS =
			java.util.prefs.Preferences.userNodeForPackage(EditorPildoraController.class).node("autosave");
	private javafx.animation.Timeline autosaveTimer;
	private boolean dirty = false;

	// Debounce para no renderizar en cada tecla
	private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(250));

	@FXML
	public void initialize() {

		// --- I18n: override hardcoded FXML text for elements with fx:id ---
		switchProteger.setText(I18n.get("switch.protect.content"));
		switchOcultar.setText(I18n.get("switch.hide.pildora"));
		txtTagInput.setPromptText(I18n.get("prompt.add.tag"));
		btnGuardar.setText(I18n.get("button.save.icon"));
		btnCancelar.setText(I18n.get("button.cancel.icon"));
		mbMath.setText(I18n.get("menu.math"));
		switchPreview.setText(I18n.get("switch.preview"));
		lblTags.setText(I18n.get("label.tags"));
		lblTitle.setText(I18n.get("label.title"));
		lblContent.setText(I18n.get("label.content.markdown"));

		smbLinks.getItems().get(0).setText(I18n.get("menu.link.external"));
		smbLinks.getItems().get(1).setText(I18n.get("menu.link.internal"));

		mbHeadings.getItems().stream()
			.filter(mi -> !(mi instanceof javafx.scene.control.SeparatorMenuItem))
			.filter(mi -> mi.getText() != null && mi.getText().startsWith("Quitar"))
			.findFirst().ifPresent(mi -> mi.setText(I18n.get("menu.remove.heading")));

		mbLists.setText(I18n.get("menu.lists"));
		mbLists.getItems().get(0).setText(I18n.get("menu.list.bullets"));
		mbLists.getItems().get(1).setText(I18n.get("menu.list.numbered"));
		mbLists.getItems().get(2).setText(I18n.get("menu.list.tasks"));

		mbBlocks.setText(I18n.get("menu.blocks"));
		mbBlocks.getItems().get(0).setText(I18n.get("menu.block.quote"));
		mbBlocks.getItems().get(1).setText(I18n.get("menu.block.code"));
		mbBlocks.getItems().get(2).setText(I18n.get("menu.block.rule"));
		mbBlocks.getItems().get(3).setText(I18n.get("menu.block.table"));

		mbInsert.setText(I18n.get("menu.insert"));
		mbInsert.getItems().get(0).setText(I18n.get("menu.insert.link"));
		mbInsert.getItems().get(1).setText(I18n.get("menu.insert.image"));

		mbAdvanced.setText(I18n.get("menu.advanced"));
		mbAdvanced.getItems().get(0).setText(I18n.get("menu.advanced.footnote"));
		mbAdvanced.getItems().get(1).setText(I18n.get("menu.advanced.toc"));
		mbAdvanced.getItems().get(2).setText(I18n.get("menu.advanced.math.inline"));
		mbAdvanced.getItems().get(3).setText(I18n.get("menu.advanced.math.block"));

		btnCancelar.setOnAction(e -> {
			clearRecoveryDraft();
			if (onClose != null)
				onClose.run();
		});

		btnGuardar.setOnAction(e -> {
			onGuardar();
		});

		// (Opcional) Tooltips con pistas
		btnGuardar.setTooltip(new Tooltip(I18n.get("tooltip.save")));
		btnCancelar.setTooltip(new Tooltip(I18n.get("tooltip.cancel.editor")));

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

		if (switchOcultar != null) {
			switchOcultar.setTooltip(new Tooltip(I18n.get("tooltip.hide.pildora")));
		}

		if (switchProteger != null) {
			switchProteger.setTooltip(new Tooltip(I18n.get("tooltip.protect.pin")));

			switchProteger.selectedProperty().addListener((obs, wasSelected, nowSelected) -> {
				if (pildoraEnEdicion == null)
					return;

				// No permitir DESPROTEGER si la píldora venía protegida y seguimos bloqueados
				if (originalProtegida && !security.isUnlocked() && !nowSelected) {
					Platform.runLater(() -> switchProteger.setSelected(true));
					StatusBus.show(I18n.get("status.unlock.to.unprotect"), StatusBus.Type.WARN, Duration.seconds(3));
					return;
				}

				if (!wasSelected && nowSelected && !originalProtegida) {
					StatusBus.show(I18n.get("status.will.protect.on.save"), StatusBus.Type.INFO, Duration.seconds(2));
				} else if (wasSelected && !nowSelected && originalProtegida) {
					txtDescripcion.setDisable(false);
					// Restaurar contenido descifrado si el campo está vacío
					String existing = txtDescripcion.getText();
					if ((existing == null || existing.isBlank()) && decryptedContentCache != null) {
						txtDescripcion.setText(decryptedContentCache);
					} else if (existing == null || existing.isBlank()) {
						recoverDecryptedContent();
					}
					StatusBus.show(I18n.get("status.will.unprotect.on.save"), StatusBus.Type.INFO, Duration.seconds(2));
				}
			});
		}

		textFormatController = new TextFormatController(root, txtDescripcion);
		textFormatController.initListIndentShortcuts(); 
		textFormatController.buildMathMenu(mbMath);
		
		root.sceneProperty().addListener((o, old, sc) -> {
		    if (sc == null) return;
		    var kc = new KeyCodeCombination(
		        KeyCode.K,
		        KeyCombination.CONTROL_DOWN,
		        KeyCombination.SHIFT_DOWN
		    );
		    sc.getAccelerators().put(kc, this::mdInsertInternalLink);
		});

		txtTitulo.textProperty().addListener((obs, o, n) -> dirty = true);
		txtDescripcion.textProperty().addListener((obs, o, n) -> dirty = true);

		autosaveTimer = new javafx.animation.Timeline(
			new javafx.animation.KeyFrame(Duration.seconds(30), ev -> saveRecoveryDraft())
		);
		autosaveTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
		autosaveTimer.play();

		offerRecoveryDraft();

		initPreview();
	}

	private void saveRecoveryDraft() {
		if (!dirty) return;
		String titulo = txtTitulo.getText();
		String contenido = txtDescripcion.getText();
		if ((titulo == null || titulo.isBlank()) && (contenido == null || contenido.isBlank())) return;
		RECOVERY_PREFS.put("titulo", titulo == null ? "" : titulo);
		RECOVERY_PREFS.put("contenido", contenido == null ? "" : contenido);
		RECOVERY_PREFS.putLong("timestamp", System.currentTimeMillis());
		if (pildoraEnEdicion != null) {
			RECOVERY_PREFS.putInt("pildoraId", pildoraEnEdicion.getId());
		} else {
			RECOVERY_PREFS.remove("pildoraId");
		}
		dirty = false;
	}

	private void clearRecoveryDraft() {
		if (autosaveTimer != null) autosaveTimer.stop();
		RECOVERY_PREFS.remove("titulo");
		RECOVERY_PREFS.remove("contenido");
		RECOVERY_PREFS.remove("timestamp");
		RECOVERY_PREFS.remove("pildoraId");
	}

	private void offerRecoveryDraft() {
		String titulo = RECOVERY_PREFS.get("titulo", null);
		String contenido = RECOVERY_PREFS.get("contenido", null);
		long ts = RECOVERY_PREFS.getLong("timestamp", 0);
		if (ts == 0 || (titulo == null && contenido == null)) return;

		long ageMinutes = (System.currentTimeMillis() - ts) / 60_000;
		if (ageMinutes > 24 * 60) {
			clearRecoveryDraft();
			return;
		}

		Platform.runLater(() -> {
			String when = ageMinutes < 1 ? I18n.get("time.less.than.1min")
					: ageMinutes < 60 ? I18n.get("time.minutes.ago", ageMinutes)
					: I18n.get("time.hours.ago", ageMinutes / 60);
			Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
			alert.setTitle(I18n.get("alert.recover.draft.title"));
			alert.setHeaderText(I18n.get("alert.recover.draft.header", when));
			alert.setContentText(I18n.get("alert.recover.draft.content"));
			Dialogs.decorate(alert, root);
			var res = alert.showAndWait();
			if (res.isPresent() && res.get() == javafx.scene.control.ButtonType.OK) {
				if (titulo != null && !titulo.isBlank() && txtTitulo.getText().isBlank()) {
					txtTitulo.setText(titulo);
				}
				if (contenido != null && !contenido.isBlank() && txtDescripcion.getText().isBlank()) {
					txtDescripcion.setText(contenido);
				}
				StatusBus.show(I18n.get("status.draft.recovered"), StatusBus.Type.INFO, Duration.seconds(3));
			}
			clearRecoveryDraft();
		});
	}

	private String tryDecrypt(Pildora p) {
		// 1) Intentar con datos del objeto pasado
		byte[] cipher = p.getDescripcionCipher();
		byte[] iv = p.getDescripcionIv();

		if (cipher != null && iv != null) {
			try {
				return security.decrypt(cipher, iv);
			} catch (Exception e) {
				System.err.println("[Editor] Descifrado falló con objeto (cipher.len="
						+ cipher.length + ", iv.len=" + iv.length + "): " + e.getMessage());
			}
		} else {
			System.err.println("[Editor] Objeto sin cipher/iv (cipher="
					+ (cipher == null ? "null" : cipher.length) + ", iv="
					+ (iv == null ? "null" : iv.length) + ")");
		}

		// 2) Recargar de BD
		try {
			Pildora fresh = pildoraDao.buscarPorId(p.getId());
			if (fresh != null) {
				byte[] fc = fresh.getDescripcionCipher();
				byte[] fiv = fresh.getDescripcionIv();
				if (fc != null && fiv != null) {
					try {
						String result = security.decrypt(fc, fiv);
						pildoraEnEdicion.setDescripcionCipher(fc);
						pildoraEnEdicion.setDescripcionIv(fiv);
						return result;
					} catch (Exception e) {
						System.err.println("[Editor] Descifrado falló con BD (cipher.len="
								+ fc.length + ", iv.len=" + fiv.length + "): " + e.getMessage());
						StatusBus.show(I18n.get("status.decrypt.error", e.getMessage()),
								StatusBus.Type.ERROR, Duration.seconds(6));
					}
				} else {
					System.err.println("[Editor] BD devolvió cipher/iv nulos para id=" + p.getId());
					StatusBus.show(I18n.get("status.db.no.cipher.data"),
							StatusBus.Type.ERROR, Duration.seconds(6));
				}
			} else {
				System.err.println("[Editor] buscarPorId devolvió null para id=" + p.getId());
				StatusBus.show(I18n.get("status.db.pildora.not.found", p.getId()),
						StatusBus.Type.ERROR, Duration.seconds(6));
			}
		} catch (Exception e) {
			System.err.println("[Editor] Error consultando BD: " + e.getMessage());
			e.printStackTrace();
			StatusBus.show(I18n.get("status.db.read.error", e.getMessage()), StatusBus.Type.ERROR, Duration.seconds(6));
		}
		return null;
	}

	private void recoverDecryptedContent() {
		txtDescripcion.setDisable(false);
		if (decryptedContentCache != null) {
			txtDescripcion.setText(decryptedContentCache);
			return;
		}
		String plain = tryDecrypt(pildoraEnEdicion);
		if (plain != null) {
			txtDescripcion.setText(plain);
			decryptedContentCache = plain;
		}
	}

	private void onGuardar() {
		String bodyForLinks = txtDescripcion.getText(); // <- antes de cifrar/limpiar

		String titulo = txtTitulo.getText().trim();
		boolean proteger = switchProteger.isSelected();

		if (titulo.isBlank()) {
			StatusBus.show(I18n.get("validation.title.required"), Type.WARN, Duration.seconds(4));
			return;
		}

		// Si venía protegida, asegurar desbloqueo y recuperar texto descifrado
		if (originalProtegida) {
			if (!security.isUnlocked()) {
				boolean ok = security
						.ensureUnlocked(() -> PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10)));
				if (!ok) {
					StatusBus.show(I18n.get("status.cannot.save.without.pin"), StatusBus.Type.ERROR,
							Duration.seconds(4));
					return;
				}
			}
			// Si el editor está vacío, restaurar contenido descifrado
			if (txtDescripcion.getText() == null || txtDescripcion.getText().isBlank()) {
				if (decryptedContentCache != null && !decryptedContentCache.isBlank()) {
					txtDescripcion.setDisable(false);
					txtDescripcion.setText(decryptedContentCache);
				} else {
					recoverDecryptedContent();
				}
			}
		}

		String descripcionPlano = txtDescripcion.getText();

		if (!proteger && (descripcionPlano == null || descripcionPlano.trim().isBlank())) {
			StatusBus.show(I18n.get("validation.description.required"), Type.WARN, Duration.seconds(4));
			return;
		}

		try {
			byte[] cipher = null, iv = null;

			if (proteger) {
				// Si no hay PIN aún, configúralo
				if (!security.hasPin()) {
					char[] pin = askNewPin6();
					if (pin == null) {
						StatusBus.show(I18n.get("status.protection.cancelled"), StatusBus.Type.WARN, Duration.seconds(3));
						return;
					}
					try {
						security.setupPin(pin);
					} finally {
						java.util.Arrays.fill(pin, '\0');
					}
				}
				// Garantiza desbloqueo con feedback consistente (status bar + segundos
				// restantes)
				if (!ensureUnlockedForEditor()) {
					return;
				}

				var enc = security.encrypt(descripcionPlano == null ? "" : descripcionPlano);
				cipher = enc.cipher;
				iv = enc.iv;
				descripcionPlano = null; // BD: NULL cuando está protegida
			}

			boolean ocultar = switchOcultar != null && switchOcultar.isSelected();

			Pildora nueva = new Pildora();
			// --- Guardado en BD ---
			if (pildoraEnEdicion == null) {
				nueva = new Pildora(titulo, descripcionPlano, proteger, cipher, iv);
				nueva.setOculta(ocultar);
				pildoraDao.insertar(nueva);
				persistTags(nueva.getId());
			} else {
				pildoraEnEdicion.setTitulo(titulo);
				pildoraEnEdicion.setProtegida(proteger);
				pildoraEnEdicion.setDescripcion(descripcionPlano);
				pildoraEnEdicion.setDescripcionCipher(cipher);
				pildoraEnEdicion.setDescripcionIv(iv);
				pildoraEnEdicion.setOculta(ocultar);

				pildoraDao.actualizar(pildoraEnEdicion);
				persistTags(pildoraEnEdicion.getId());
			}
			
			try {
				var ids = extractLinkedIds(bodyForLinks);
				new PildoraLinkDao().replaceRefs((pildoraEnEdicion == null ? nueva.getId() : pildoraEnEdicion.getId()), ids);
			} catch (Exception ex) {
				StatusBus.show(I18n.get("status.refs.sync.failed"), StatusBus.Type.WARN, Duration.seconds(3));
			}


			// --- Mensaje único + re-bloqueo si procede ---
			boolean becameProtected = (pildoraEnEdicion == null && switchProteger.isSelected())
					|| (pildoraEnEdicion != null && !originalProtegida && switchProteger.isSelected());

			String msg = I18n.get("status.pildora.saved");
			if (becameProtected) {
				SecurityService.getInstance().lockNow();
				msg = I18n.get("status.pildora.saved.protected");
			}

			StatusBus.show(msg, StatusBus.Type.SUCCESS, Duration.seconds(3));

			clearRecoveryDraft();
			if (onClose != null)
				onClose.run();

		} catch (Exception ex) {
			ex.printStackTrace();
			StatusBus.show(I18n.get("status.error.saving", ex.getMessage()), Type.ERROR, Duration.seconds(4));
		}
	}
	
	private static java.util.Set<Integer> extractLinkedIds(String md) {
	    var ids = new HashSet<Integer>();
	    if (md == null || md.isBlank()) return ids;
	    var m = P_PILDORA_ID.matcher(md);
	    while (m.find()) {
	        try {
	            int id = Integer.parseInt(m.group(1));
	            if (id > 0) ids.add(id);
	        } catch (NumberFormatException ignore) {}
	    }
	    return ids;
	}

	private char[] askPin6() {
		var dlg = new javafx.scene.control.TextInputDialog();
		dlg.setTitle(I18n.get("pin.dialog.title"));
		dlg.setHeaderText(I18n.get("pin.dialog.header"));
		dlg.setContentText(I18n.get("label.pin"));
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
			StatusBus.show(I18n.get("status.pin.mismatch"), Type.WARN, Duration.seconds(3));
			java.util.Arrays.fill(p1, '\0');
			return null;
		}
		return p1;
	}

	public void cargarPildora(Pildora p) {
		this.pildoraEnEdicion = p;
		this.originalProtegida = p.isProtegida();
		this.decryptedContentCache = null;
		txtTitulo.setText(p.getTitulo());
		if (switchOcultar != null)
			switchOcultar.setSelected(p.isOculta());

		if (p.isProtegida()) {
			if (security.isUnlocked()) {
				String plano = tryDecrypt(p);
				if (plano != null) {
					txtDescripcion.setDisable(false);
					txtDescripcion.setText(plano);
					decryptedContentCache = plano;
				} else {
					txtDescripcion.clear();
					txtDescripcion.setDisable(true);
					txtDescripcion.setPromptText(I18n.get("editor.decrypt.error.prompt"));
				}
			} else {
				txtDescripcion.clear();
				txtDescripcion.setDisable(true);
				txtDescripcion.setPromptText(I18n.get("editor.content.protected.prompt"));
			}
		} else {
			txtDescripcion.setDisable(false);
			txtDescripcion.setText(p.getDescripcion() == null ? "" : p.getDescripcion());
		}

		// Poner el toggle DESPUÉS de cargar el contenido para evitar que el listener interfiera
		if (switchProteger != null)
			switchProteger.setSelected(p.isProtegida());

		currentTags.clear();
		tagsBox.getChildren().clear();
		tagDao.findByPildoraId(p.getId()).forEach(t -> addTagChip(t.getNombre()));
	}

	/** Muestra un modal con el tiempo restante del lockout (tema aplicado). */
	private void showLockoutModal() {

		long secs = Math.max(0L, (security.lockoutRemainingMillis() + 999) / 1000);

		Alert a = new Alert(Alert.AlertType.WARNING);
		a.setTitle(I18n.get("alert.lockout.title2"));
		a.setHeaderText(I18n.get("alert.lockout.header"));
		a.setContentText(I18n.get("alert.lockout.content", secs));
		Dialogs.decorate(a, root); // 👈 mantiene el tema/estilo de la app
		a.showAndWait();
	}

	/** Desbloqueo con reintentos y modal si está bloqueado. */
	private boolean ensureUnlockedForEditor() {

		// Ya desbloqueado o recordado
		if (security.isUnlocked() || security.isRemembered())
			return true;

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
			if (pin == null)
				return false; // cancelado

			try {
				if (security.verifyPin(pin)) {
					StatusBus.show(I18n.get("status.unlocked.10min"), StatusBus.Type.INFO, Duration.seconds(3));
					return true;
				} else {
					attempts++;
					// Si justo aquí entra en lockout, mostrar MODAL (no status bar)
					if (security.isLockedOut()) {
						showLockoutModal();
						return false;
					}
					int left = MAX - attempts;
					StatusBus.show(I18n.get("alert.pin.incorrect.remaining", left),
							StatusBus.Type.WARN, Duration.seconds(3));
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
		box.setStyle(String.format("-chip-bg: %s; -chip-fg: %s;", bg, fg));
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
				box.setStyle(String.format("-chip-bg: %s; -chip-fg: %s;", bg, fg));
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
			if (e.isControlDown() && (e.getCode() == KeyCode.S || e.getCode() == KeyCode.ENTER)) {
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
				insertarTexto("**" + I18n.get("md.placeholder.bold") + "**");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.I) {
				insertarTexto("*" + I18n.get("md.placeholder.italic") + "*");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.K) {
				insertarTexto("[" + I18n.get("md.link.default.text") + "](https://)");
				e.consume();
				return;
			}
			if (e.isControlDown() && e.getCode() == KeyCode.E) {
				insertarTexto("`" + I18n.get("md.placeholder.code") + "`");
				e.consume();
				return;
			}
		};

		rootNode.addEventFilter(KeyEvent.KEY_PRESSED, editorKeyHandler);
	}

	@FXML
	public void mdBold() {
		textFormatController.mdBold();
	}

	@FXML
	public void mdItalic() {
		textFormatController.mdItalic();
	}

	@FXML
	public void mdStrike() {
		textFormatController.mdStrike();
	}

	@FXML
	public void mdCodeInline() {
		textFormatController.mdCodeInline();
	}

	@FXML
	public void mdH1() {
		textFormatController.mdH1();
	}

	@FXML
	public void mdH2() {
		textFormatController.mdH2();
	}

	@FXML
	public void mdH3() {
		textFormatController.mdH3();
	}

	@FXML
	public void mdClearHeading() {
		textFormatController.mdH3();
	}

	@FXML
	public void mdBulletedList() {
		textFormatController.mdBulletedList();
	}

	@FXML
	public void mdNumberedList() {
		textFormatController.mdNumberedList();
	}

	@FXML
	public void mdTaskList() {
		textFormatController.mdTaskList();
	}

	@FXML
	public void mdBlockquote() {
		textFormatController.mdBlockquote();
	}

	@FXML
	public void mdCodeBlock() {
		textFormatController.mdCodeBlock();
	}

	@FXML
	public void mdHorizontalRule() {
		textFormatController.mdHorizontalRule();
	}

	@FXML
	public void mdTable2x2() {
		textFormatController.mdTable2x2();
	}

	@FXML
	public void mdInsertLinkDialog() {
		textFormatController.mdInsertLinkDialog();
	}

	@FXML
	public void mdInsertImageDialog() {
		textFormatController.mdInsertImageDialog();
	}

	@FXML
	public void mdFootnote() {
		textFormatController.mdFootnote();
	}

	@FXML
	public void mdInsertTOC() {
		textFormatController.mdInsertTOC();
	}

	@FXML
	public void mdMathInline() {
		textFormatController.mdMathInline();
	}

	@FXML
	public void mdMathBlock() {
		textFormatController.mdMathBlock();
	}

	@FXML
	private void mostrarAyuda() {
		Alert ayuda = new Alert(Alert.AlertType.INFORMATION);
		ayuda.setTitle(I18n.get("help.markdown.title"));
		ayuda.setHeaderText(I18n.get("help.markdown.header"));
		ayuda.setContentText(I18n.get("help.markdown.content"));
		ayuda.showAndWait();
	}
	
	@FXML
	public void mdInsertInternalLink() {
	    textFormatController.mdInsertInternalLink();
	}

	private void insertarTexto(String snippet) {
		int pos = txtDescripcion.getCaretPosition();
		txtDescripcion.insertText(pos, snippet);
		txtDescripcion.requestFocus();
	}

	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	// DRAFTS
	public void setPrefill(String titulo, String cuerpo, boolean proteger) {
		txtTitulo.setText(titulo == null ? "" : titulo);
		switchProteger.setSelected(proteger);
		// si proteger = true y sesión desbloqueada, permitimos edición directa;
		// si está bloqueado, tu lógica existente ya mantiene el TextArea
		// disabled/placeholder.
		txtDescripcion.setDisable(proteger && !security.isUnlocked());
		txtDescripcion.setText(cuerpo == null ? "" : cuerpo);
	}

	private static String loadResourceAsString(String path) {
		try (var is = EditorPildoraController.class.getResourceAsStream(path)) {
			if (is == null)
				return "";
			return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	private void initPreview() {
		cssLight    = loadResourceAsString("/css/webview-light.css");
		cssDark     = loadResourceAsString("/css/webview-dark.css");
		cssBaseWeb  = loadResourceAsString("/css/base-webview.css");

		// Estado inicial: OFF
		switchPreview.setSelected(false);
		switchPreview.setTooltip(new Tooltip(I18n.get("tooltip.preview.markdown")));
		syncPreview(false);

		// Debounce solo si ON
		previewDebounce.setOnFinished(e -> renderPreviewNow());
		txtDescripcion.textProperty().addListener((obs, old, val) -> {
			if (switchPreview.isSelected())
				previewDebounce.playFromStart();
		});

		// Toggle ON/OFF
		switchPreview.selectedProperty().addListener((o, wasOn, nowOn) -> syncPreview(nowOn));

		// Re-render al cambiar tema (si ON)
		txtDescripcion.sceneProperty().addListener((obs, oldScene, scene) -> {
			if (scene == null)
				return;
			scene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) c -> {
				if (switchPreview.isSelected())
					renderPreviewNow();
			});
		});
	}

	private void syncPreview(boolean on) {
		webPreview.setManaged(on);
		webPreview.setVisible(on);
		if (on) {
			if (splitEditor.getDividerPositions().length > 0) {
				splitEditor.setDividerPositions(0.5);
			}
			renderPreviewNow();
		} else {
			splitEditor.setDividerPositions(1.0);
		}
	}

	// Decide CSS según tema actual
	@SuppressWarnings("unused")
	private String currentCssForTheme() {
		var isDark = io.github.guillermo_david.javafx.ThemeManager
				.load() == io.github.guillermo_david.javafx.ThemeManager.Theme.DARK;
		return isDark ? cssDark : cssLight;
	}

	// Renderiza ya (sin debounce)
	private void renderPreviewNow() {
	    String mdText = txtDescripcion.getText();
	    String body = md.toHtml(mdText);

	    boolean wantsMath = mdText != null && (mdText.contains("$$") || mdText.contains("$"));
	    String themeCss = (ThemeManager.load() == ThemeManager.Theme.DARK) ? cssDark : cssLight;
	    String cssAll   = cssBaseWeb + "\n" + themeCss;


	    String math = wantsMath ? """
	        <script>
	          window.MathJax = { tex: { inlineMath: [['$', '$'], ['\\\\(', '\\\\)']] }, svg: {fontCache: 'global'} };
	        </script>
	        <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
	        """ : "";

	    String html = """
	        <!doctype html>
	        <html>
	        <head>
	          <meta charset="utf-8">
	          <style>
	          		""" + cssAll + """
	          </style>
	        </head>
	        <body class="md-body">
	        """ + body + math + """
	        </body>
	        </html>
	        """;

	    webPreview.getEngine().loadContent(
	        """
	        <!doctype html><html><head>
	          <meta charset="UTF-8">
	          <meta name="color-scheme" content="dark light">
	          <style>%s</style>
	        </head><body class="md-body">%s</body></html>
	        """.formatted(cssAll, html)
	    );
//	    webPreview.getEngine().loadContent(html, "text/html");
	}
	
}
