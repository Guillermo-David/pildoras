package io.github.guillermo_david.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.controlsfx.control.textfield.CustomTextField;
import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.MainApp;
import io.github.guillermo_david.dao.DraftDao;
import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.DraftDialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.javafx.ThemeManager.Theme;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ListadoPildorasController {

	private enum SortState {
		ASC, DESC, NONE
	}

	private final SecurityService security = SecurityService.getInstance();

	private final PildoraDao pildoraDao = new PildoraDao();
	private final TagDao tagDao = new TagDao();

	private int paginaActual = 1;
	private final int TAMANIO_PAGINA = 20;
	private int totalPaginas = 1;

	private boolean uiOculta = false;
	private boolean shortcutsInstalados = false;
	private boolean ignoreNextDelete = false;

	private static final String DEFAULT_ORDER_COL = "fecha_creacion";
	private static final String DEFAULT_ORDER_DIR = "DESC";
	private String columnaOrden = DEFAULT_ORDER_COL;
	private String direccionOrden = DEFAULT_ORDER_DIR;
	private TableColumn<Pildora, ?> lastSortColumn = null;
	
	// Campos (arriba en el controller)
	private long lastWheelNanos = 0L;
	private static final long WHEEL_THROTTLE_NS = 180_000_000L; // ~180 ms

	private SortState lastState = SortState.NONE;
	private boolean suppressSort = false;

	private static final Preferences PREFS = Preferences.userNodeForPackage(ListadoPildorasController.class);
	private static final String PREF_SOLO_FAV = "soloFavoritas";
	private static final String PREF_AND_OR = "filtroAndOr";

	private static final String PREF_FONT_FAMILY = "uiFontFamily";
	private static final String PREF_FONT_SIZE = "uiFontSize"; // "small" | "normal" | "large"
	private static final String DEF_FONT_FAMILY = "System";
	private static final String DEF_FONT_SIZE = "normal";
	private final int DEFAULT_SMALL_ICON_SIZE = 14;

	private double dragOffsetX, dragOffsetY;
	
	private Label draftsBadge;
	private StackPane draftsIconStack;


	String base = null;
	String light = null;
	String dark = null;

	private Node centerBackup;

	@FXML private BorderPane root;
	@FXML private Button btnNueva, btnAnterior, btnSiguiente, btnClose;
	@FXML private ToggleButton btnAndOr, btnSoloFav;
	@FXML private MenuButton btnSettings, btnBorradores;
	@FXML private HBox paginationBox, statusBar, titleBar, appHeader;
	@FXML private Label lblPagina, lblStatus;
	@FXML private TableView<Pildora> table;
	@FXML private TableColumn<Pildora, String> colTitulo, colDescripcion, colTags;
	@FXML private TableColumn<Pildora, Void> colFav, colAcciones, colPinned;
	@FXML private CustomTextField txtFiltroTexto, txtFiltroTags;
	@FXML private ImageView imgLogo;

	@FXML
	public void initialize() {

		base = MainApp.class.getResource("/css/base.css").toExternalForm();
		light = MainApp.class.getResource("/css/theme-light.css").toExternalForm();
		dark = MainApp.class.getResource("/css/theme-dark.css").toExternalForm();

		setStatusBar();
		setTableProperties();
		setColFav();
		setColPinned();
		setColTitulo();
		setColDescripcion();
		setColTags();
		setColAcciones();
		setBotones();
		initSettingsMenu();
		applyTypographyNow();
		instalarWheelPagination();
		cargarTabla(null, null);
		setLogoFor(ThemeManager.load());
		setTxtFiltros();
		setSceneProperties();
//	    initThemeToggle();
//	    syncThemeToggleWithCurrent();
//	    hookLogoToTheme();

		// Barra de título custom
		initCustomTitleBar();

		if (centerBackup == null) {
			centerBackup = root.getCenter(); // guarda el “centro del listado” inicial
		}

	}

	private void setTxtFiltros() {
		txtFiltroTexto.setOnAction(e -> {
			paginaActual = 1;
			refrescarTabla();
		});
		txtFiltroTags.setOnAction(e -> {
			paginaActual = 1;
			refrescarTabla();
		});

		txtFiltroTexto.setTooltip(new Tooltip("Ctrl+F"));
		txtFiltroTags.setTooltip(new Tooltip("Ctrl+T"));

		attachClearButton(txtFiltroTexto, () -> {
			paginaActual = 1;
			refrescarTabla();
		});
		attachClearButton(txtFiltroTags, () -> {
			paginaActual = 1;
			refrescarTabla();
		});

		// Ancho base (un poco más grandes)
		txtFiltroTexto.setPrefWidth(275);
		txtFiltroTexto.setMinWidth(200);
		txtFiltroTexto.setMaxWidth(Double.MAX_VALUE);

		txtFiltroTags.setPrefWidth(275);
		txtFiltroTags.setMinWidth(200);
		txtFiltroTags.setMaxWidth(Double.MAX_VALUE);

		// Que puedan crecer si hay hueco en la barra
		HBox.setHgrow(txtFiltroTexto, Priority.ALWAYS);
		HBox.setHgrow(txtFiltroTags, Priority.ALWAYS);

		// (opcional) por columnas, por si prefieres afinar por caracteres
//	     txtFiltroTexto.setPrefColumnCount(24);
//	     txtFiltroTags.setPrefColumnCount(18);

	}

	private void setStatusBar() {
		StatusBus.messageProperty().addListener((obs, oldMsg, msg) -> {
			if (msg == null)
				return;

			HBox.setHgrow(lblStatus, Priority.ALWAYS);
			lblStatus.setMaxWidth(Double.MAX_VALUE);
			lblStatus.setStyle("-fx-alignment: center;");
			// ✅ hazlo en el contenedor
			statusBar.getStyleClass().removeAll("status-info", "status-success", "status-warn", "status-error");
			switch (msg.type) {
			case INFO -> statusBar.getStyleClass().add("status-info");
			case SUCCESS -> statusBar.getStyleClass().add("status-success");
			case WARN -> statusBar.getStyleClass().add("status-warn");
			case ERROR -> statusBar.getStyleClass().add("status-error");
			}
			lblStatus.setText(msg.text);
			lblStatus.setWrapText(true); // opcional si los mensajes pueden ser largos

			// TTL: limpiar tras X segundos
			var ttl = msg.ttl != null ? msg.ttl : Duration.seconds(3);
			// Usa un PauseTransition para limpiar
			var pause = new PauseTransition(ttl);
			pause.setOnFinished(e -> {
				// borra solo si no ha llegado un mensaje nuevo
				if (StatusBus.messageProperty().get() == msg) {
					lblStatus.setText("");
					statusBar.getStyleClass().removeAll("status-info", "status-success", "status-warn", "status-error");
				}
			});
			pause.play();
		});
	}

	@SuppressWarnings("unchecked")
	private void setTableProperties() {
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setFixedCellSize(30); // alto por fila
		table.setPlaceholder(new Label("Sin resultados"));
		table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
			if (newSkin == null)
				return;
			Platform.runLater(() -> {
				Region headerBg = (Region) table.lookup(".column-header-background");
				var headerHeight = headerBg != null ? headerBg.heightProperty() : new SimpleDoubleProperty(28);

				double row = table.getFixedCellSize(); // 30
				int rows = TAMANIO_PAGINA; // 20

				table.prefHeightProperty().unbind();
				table.prefHeightProperty().bind(headerHeight.add(rows * row + 2));

				table.setMinHeight(Region.USE_COMPUTED_SIZE);
				table.setMaxHeight(Region.USE_COMPUTED_SIZE);

				var wnd = table.getScene() != null ? table.getScene().getWindow() : null;
				if (wnd instanceof Stage st)
					Platform.runLater(st::sizeToScene);
			});
		});

		colTitulo.setSortable(true);
		colDescripcion.setSortable(true);
		colTags.setSortable(false);
		colFav.setSortable(false);
		colPinned.setSortable(false);
		colAcciones.setSortable(false);

		table.setSortPolicy(tv -> {
			if (suppressSort)
				return true;

			TableColumn<Pildora, ?> primary = tv.getSortOrder().isEmpty() ? null : tv.getSortOrder().get(0);

			// --- 3er click: JavaFX ya limpió sortOrder (primary == null). Forzamos NONE +
			// orden por defecto.
			if (primary == null && lastSortColumn != null && lastState != SortState.NONE) {
				suppressSort = true;
				try {
					tv.getSortOrder().clear(); // sin flecha
					lastSortColumn = null;
					lastState = SortState.NONE;
				} finally {
					suppressSort = false;
				}
				columnaOrden = DEFAULT_ORDER_COL; // "fecha_creacion"
				direccionOrden = DEFAULT_ORDER_DIR; // "DESC"
				paginaActual = 1;
				refrescarTabla();
				return true; // ya gestionado
			}

			if (primary == null)
				return true; // no hay transición (p.ej. click fuera), no hacer nada

			SortState next;
			if (primary == lastSortColumn) {
				next = switch (lastState) {
				case ASC -> SortState.DESC;
				case DESC -> SortState.NONE;
				case NONE -> SortState.ASC;
				};
			} else {
				next = SortState.ASC;
			}

			suppressSort = true;
			try {
				if (next == SortState.NONE) {
					tv.getSortOrder().clear();
					lastSortColumn = null;
					lastState = SortState.NONE;

					columnaOrden = DEFAULT_ORDER_COL;
					direccionOrden = DEFAULT_ORDER_DIR;

					paginaActual = 1;
					refrescarTabla();
				} else {
					if (tv.getSortOrder().isEmpty() || tv.getSortOrder().get(0) != primary) {
						tv.getSortOrder().setAll(primary);
					}
					primary.setSortType(
							next == SortState.ASC ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);

					if (primary == colTitulo) {
						columnaOrden = "titulo";
					} else if (primary == colDescripcion) {
						columnaOrden = "descripcion";
					} else {
						tv.getSortOrder().clear();
						lastSortColumn = null;
						lastState = SortState.NONE;
						columnaOrden = DEFAULT_ORDER_COL;
						direccionOrden = DEFAULT_ORDER_DIR;

						paginaActual = 1;
						refrescarTabla();
						return true;
					}

					direccionOrden = (next == SortState.ASC) ? "ASC" : "DESC";

					lastSortColumn = primary;
					lastState = next;

					paginaActual = 1;
					refrescarTabla();
				}
			} finally {
				suppressSort = false;
			}

			return true;
		});

		table.setRowFactory(tv -> {
			TableRow<Pildora> row = new TableRow<>();
			row.setOnMouseClicked(event -> {
				if (!row.isEmpty() && event.getClickCount() == 2) {
					Pildora p = row.getItem();
					if (p.isProtegida() && !security.isUnlocked()) {
						if (!ensureUnlockedWithRetries()) {
							// Mensajes ya dados dentro; abortar navegación
							return;
						}
					}
					abrirDetallePildora(p);
				}
			});
			return row;
		});

		// Columnas fijas para pin y fav
		colPinned.setMinWidth(45);
		colPinned.setPrefWidth(45);
		colPinned.setMaxWidth(45);
		colPinned.setResizable(false);

		colFav.setMinWidth(45);
		colFav.setPrefWidth(45);
		colFav.setMaxWidth(45);
		colFav.setResizable(false);

	}

	private char[] promptPinOnce() {
		var dialog = new Dialog<char[]>();
		dialog.setTitle("PIN requerido");
		dialog.setHeaderText("Introduce tu PIN (6 dígitos)");

		var pf = new PasswordField();
		pf.setPromptText("******");
		pf.setTextFormatter(new TextFormatter<String>(c -> {
			if (!c.getControlNewText().matches("\\d{0,6}"))
				return null;
			return c;
		}));

		var remember = new CheckBox("Recordar durante 10 minutos");
		var content = new VBox(8, new Label("PIN:"), pf, remember);
		dialog.getDialogPane().setContent(content);

		var okBtn = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
		var cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().setAll(okBtn, cancel);

		var okNode = dialog.getDialogPane().lookupButton(okBtn);
		okNode.setDisable(true);
		pf.textProperty().addListener((o, a, b) -> okNode.setDisable(b == null || b.length() != 6));

		dialog.setResultConverter(bt -> {
			if (bt == okBtn) {
				if (remember.isSelected()) {
					security.startRememberWindow(java.time.Duration.ofMinutes(10));
				}
				return pf.getText().toCharArray();
			}
			return null;
		});

		var res = dialog.showAndWait();
		return res.orElse(null);
	}

	private boolean ensureUnlockedWithRetries() {
		var security = SecurityService.getInstance();

		// Ya desbloqueado o recordado -> OK
		if (security.isUnlocked() || security.isRemembered())
			return true;

		// Bloqueado ahora mismo -> avisa temado y corta
		if (security.isLockedOut()) {
			showLockoutAlert();
			return false;
		}

		final int MAX = 3; // debe coincidir con MAX_FAILED del servicio
		int attempts = 0;

		while (attempts < MAX) {
			// PIN temado + foco + “recordar 10 min”
			char[] pin = PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10));
			if (pin == null)
				return false; // cancelado

			try {
				if (security.verifyPin(pin)) {
					StatusBus.show("Desbloqueado por 10 minutos.", StatusBus.Type.INFO, Duration.seconds(3));
					return true;
				} else {
					attempts++;
					// ¿acaba de bloquearse?
					if (security.isLockedOut()) {
						showLockoutAlert();
						return false;
					}
					int left = MAX - attempts;
					Alert warn = new Alert(AlertType.WARNING);
					warn.setTitle("PIN incorrecto");
					warn.setHeaderText(null);
					warn.setContentText(
							left > 0 ? "PIN incorrecto. Te quedan " + left + " intento" + (left == 1 ? "" : "s") + "."
									: "Has agotado los intentos.");
					Dialogs.decorate(warn, root); // 👈 temado igual que el resto
					warn.showAndWait();
				}
			} finally {
				Arrays.fill(pin, '\0');
			}
		}

		// Si salimos por agotar intentos, muestra lockout si aplica
		if (security.isLockedOut())
			showLockoutAlert();
		return false;
	}

	private void showLockoutAlert() {
		var security = SecurityService.getInstance();
		long secs = (security.lockoutRemainingMillis() + 999) / 1000;
		Alert lock = new Alert(AlertType.ERROR);
		lock.setTitle("Demasiados intentos");
		lock.setHeaderText("Has excedido los intentos de PIN");
		lock.setContentText("Vuelve a intentarlo en " + secs + " segundos.");
		Dialogs.decorate(lock, root); // 👈 temado
		lock.showAndWait();
	}

	private boolean unlockWithFeedback() {

		if (security.isUnlocked())
			return true;

		long lockMs = security.lockoutRemainingMillis();
		if (lockMs > 0) {
			StatusBus.show("Has agotado los intentos. Vuelve a probar en " + humanize(lockMs) + ".",
					StatusBus.Type.ERROR, Duration.seconds(4));
			return false;
		}

		char[] pin = promptPinOnce();
		if (pin == null)
			return false;

		try {
			boolean ok = security.verifyPin(pin);
			if (ok) {
				StatusBus.show("Desbloqueado.", StatusBus.Type.INFO, Duration.seconds(2));
				return true;
			} else {
				if (security.isLockedOut()) {
					long ms = security.lockoutRemainingMillis();
					StatusBus.show("Has agotado los intentos. Vuelve a probar en " + humanize(ms) + ".",
							StatusBus.Type.ERROR, Duration.seconds(4));
				} else {
					int left = security.attemptsLeft();
					StatusBus.show("PIN incorrecto. Te quedan " + left + " intento" + (left == 1 ? "" : "s") + ".",
							StatusBus.Type.ERROR, Duration.seconds(3));
				}
				return false;
			}
		} finally {
			Arrays.fill(pin, '\0');
		}
	}

	private static String humanize(long ms) {
		long s = (ms + 999) / 1000;
		if (s < 60)
			return s + " s";
		long m = s / 60, rs = s % 60;
		return rs == 0 ? (m + " min") : (m + " min " + rs + " s");
	}

	private void setColFav() {
		colFav.setSortable(false);
		colFav.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || getTableRow() == null || getTableRow().getItem() == null) {
					setGraphic(null);
					return;
				}

				Pildora p = (Pildora) getTableRow().getItem();

				Button star = new Button();
				star.getStyleClass().add("star"); // 👈 para .button.star del CSS

				FontIcon icon = new FontIcon(p.isFavorita() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
				icon.getStyleClass().add("star-icon"); // base para tamaño/color
				icon.setIconSize(DEFAULT_SMALL_ICON_SIZE);
				if (p.isFavorita()) {
					icon.getStyleClass().add("star-fav"); // 👈 color dorado
				}

				star.setGraphic(icon);
				star.setOnAction(e -> {
					boolean nueva = !p.isFavorita();
					pildoraDao.marcarFavorita(p.getId(), nueva);
					p.setFavorita(nueva);

					// Actualiza icono y clases
					icon.setIconCode(nueva ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
					icon.getStyleClass().remove("star-fav");
					if (nueva)
						icon.getStyleClass().add("star-fav");

					StatusBus.show(nueva ? "Añadida a favoritas" : "Quitada de favoritas", StatusBus.Type.INFO,
							Duration.seconds(2));
					if (btnSoloFav.isSelected())
						refrescarTabla();
				});

				HBox box = new HBox(star);
				box.setStyle("-fx-alignment: CENTER;");
				box.setTranslateX(-4);
				setGraphic(box);
			}
		});
	}

	private void setColPinned() {
		colPinned.setSortable(false);
		colPinned.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || getTableRow() == null || getTableRow().getItem() == null) {
					setGraphic(null);
					return;
				}

				Pildora p = (Pildora) getTableRow().getItem();

				Button pinBtn = new Button();
				pinBtn.getStyleClass().add("pin");

				// 👇 siempre Solid
				FontIcon icon = new FontIcon(FontAwesomeSolid.THUMBTACK);
				icon.setIconSize(DEFAULT_SMALL_ICON_SIZE);
				icon.getStyleClass().add("pin-icon");
				if (p.isPinned())
					icon.getStyleClass().add("pin-active");
				icon.setRotate(p.isPinned() ? -20 : 0); // opcional: efecto clavada

				pinBtn.setGraphic(icon);
				pinBtn.setTooltip(new Tooltip(p.isPinned() ? "Desfijar" : "Fijar"));

				pinBtn.setOnAction(e -> {
					boolean nuevo = !p.isPinned();
					pildoraDao.marcarPinned(p.getId(), nuevo);
					p.setPinned(nuevo);

					icon.getStyleClass().remove("pin-active");
					if (nuevo)
						icon.getStyleClass().add("pin-active");
					icon.setRotate(nuevo ? -20 : 0);
					pinBtn.setTooltip(new Tooltip(nuevo ? "Desfijar" : "Fijar"));

					StatusBus.show(nuevo ? "Píldora anclada" : "Píldora desanclada", StatusBus.Type.INFO,
							Duration.seconds(2));

					// si el ORDER BY usa pinned DESC, recarga la tabla para recolocar
					ListadoPildorasController.this.refrescarTabla();
				});

				HBox box = new HBox(pinBtn);
				box.setStyle("-fx-alignment: CENTER;");
				setGraphic(box);
			}
		});
	}

	private void setBotones() {
		// --- SOLO FAVORITAS ---
		FontIcon favTopIcon = new FontIcon(btnSoloFav.isSelected() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
		favTopIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);

		btnSoloFav.getStyleClass().setAll("round-toggle", "fav-toggle"); // <- aquí
		btnSoloFav.setGraphic(favTopIcon);
		btnSoloFav.setText(null);
		btnSoloFav.setTooltip(new Tooltip("Solo favoritas (Ctrl+Shift+F)"));

		btnSoloFav.selectedProperty().addListener((obs, old, sel) -> {
			favTopIcon.setIconCode(sel ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
			PREFS.putBoolean(PREF_SOLO_FAV, sel);
			paginaActual = 1;
			refrescarTabla();

			StatusBus.show(sel ? "Mostrando solo favoritas" : "Mostrando todas las píldoras", StatusBus.Type.INFO,
					Duration.seconds(2));
		});

		// --- AND / OR ---
		FontIcon btnAndOrIcon = new FontIcon(
				btnAndOr.isSelected() ? FontAwesomeSolid.LINK : FontAwesomeSolid.CODE_BRANCH);
		btnAndOrIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);

		btnAndOr.getStyleClass().setAll("round-toggle", "andor-toggle"); // <- y aquí
		btnAndOr.setGraphic(btnAndOrIcon);
		btnAndOr.setText(null);
		btnAndOr.setTooltip(new Tooltip("Alterna entre al menos un tag / todos los tags (Ctrl+Shift+O)"));

		btnAndOr.selectedProperty().addListener((obs, o, sel) -> {
			btnAndOrIcon.setIconCode(sel ? FontAwesomeSolid.LINK : FontAwesomeSolid.CODE_BRANCH);
			PREFS.putBoolean(PREF_AND_OR, sel);
			paginaActual = 1;
			refrescarTabla();

			StatusBus.show(sel ? "Filtro de tags: contiene todas" : "Filtro de tags: contiene al menos una",
					StatusBus.Type.INFO, Duration.seconds(2));
		});

		FontIcon nuevaIcon = new FontIcon(FontAwesomeSolid.FOLDER_PLUS);
		btnNueva.setGraphic(nuevaIcon);
		btnNueva.setText("");
		btnNueva.setTooltip(new Tooltip("Nueva (Ctrl+N)"));
		btnNueva.setOnAction(e -> abrirFormularioNueva());
		btnNueva.getStyleClass().add("icon-btn");
		btnNueva.getStyleClass().add("btn-big");

		btnAnterior.setTooltip(new Tooltip("Página anterior (Ctrl+← / PageUp)"));
		btnAnterior.setOnAction(e -> {
			if (paginaActual > 1) {
				paginaActual--;
				refrescarTabla();
			}
		});

		btnSiguiente.setTooltip(new Tooltip("Página siguiente (Ctrl+→ / PageDown)"));
		btnSiguiente.setOnAction(e -> {
			if (paginaActual < totalPaginas) {
				paginaActual++;
				refrescarTabla();
			}
		});

		if (btnBorradores != null) initDraftsUI();
	}

	private void setColTitulo() {
		colTitulo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTitulo()));
		colTitulo.setMaxWidth(200);
		colTitulo.setCellFactory(col -> new TableCell<>() {
			private final Label lbl = new Label();
			{
				lbl.setWrapText(false); // no partir en varias líneas
				lbl.setMaxWidth(Double.MAX_VALUE);
				lbl.setTextOverrun(OverrunStyle.ELLIPSIS); // "..."
			}

			@Override
			protected void updateItem(String value, boolean empty) {
				super.updateItem(value, empty);
				if (empty || value == null) {
					setGraphic(null);
				} else {
					lbl.setText(value);
					// tooltip con el texto completo solo si se corta
					var tt = new Tooltip(value);
					lbl.setTooltip(tt);
					setGraphic(lbl);
				}
			}
		});
	}

	private void setColDescripcion() {
		colDescripcion.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescripcion()));
		colDescripcion.setCellFactory(col -> new TableCell<>() {
			private final Label lbl = new Label();
			private final FontIcon lock = new FontIcon(FontAwesomeSolid.LOCK);
			private final HBox wrap = new HBox(6, lock, lbl);
			{
				lbl.setWrapText(false);
				lbl.setMaxWidth(Double.MAX_VALUE);
				lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
				lock.setIconSize(12); // pequeño
				lock.getStyleClass().addAll("muted-icon", "danger"); // opcional: dale un color atenuado en tu CSS

			}

			@Override
			protected void updateItem(String value, boolean empty) {
				super.updateItem(value, empty);
				if (empty || getTableRow() == null || getTableRow().getItem() == null) {
					setGraphic(null);
					return;
				}
				Pildora p = (Pildora) getTableRow().getItem();
				if (p.isProtegida()) {
					lbl.setText("Contenido protegido");
					lbl.setTooltip(null); // sin tooltip
					setGraphic(wrap); // 🔒 + texto
				} else {
					lbl.setText(value);
					lbl.setTooltip(value == null || value.isBlank() ? null : new Tooltip(value));
					setGraphic(lbl); // sólo texto
				}
			}
		});
	}

	private void setColTags() {
		colTags.setCellValueFactory(cell -> {
			List<Tag> tags = tagDao.findByPildoraId(cell.getValue().getId());
			String nombres = tags.stream().map(Tag::getNombre).collect(Collectors.joining(", "));
			return new SimpleStringProperty(nombres);
		});
		colTags.setMaxWidth(300);
		colTags.setSortable(false);
	}

	private void setColAcciones() {
		colAcciones.setMaxWidth(80);
		colAcciones.setSortable(false);

		colAcciones.setCellFactory(col -> new TableCell<>() {
			private final Button btnEditar = iconButton(new FontIcon(FontAwesomeRegular.EDIT), "Editar (Ctrl+E)",
					() -> {
						Pildora p = (Pildora) getTableRow().getItem();
						if (p == null)
							return;
						if (p.isProtegida() && !security.isUnlocked()) {
							if (!ensureUnlockedWithRetries())
								return; // corta si no desbloquea
						}
						abrirFormularioEditar(p);
					});

			private final Button btnBorrar = iconButton(new FontIcon(FontAwesomeSolid.TRASH), "Eliminar (Supr)", () -> {
				Pildora p = (Pildora) getTableRow().getItem();
				if (p == null)
					return;

				if (p.isProtegida() && !security.isUnlocked()) {
					if (!ensureUnlockedWithRetries())
						return; // no borrar si no desbloquea
				}

				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
						"¿Seguro que quieres eliminar la píldora \"" + p.getTitulo() + "\"?");
				Dialogs.decorate(confirm, root);
				confirm.showAndWait().ifPresent(res -> {
					if (res.getButtonData().isDefaultButton()) {
						pildoraDao.eliminar(p.getId());
						StatusBus.show("Píldora eliminada.", StatusBus.Type.INFO, Duration.seconds(3));
						refrescarTabla();
					}
				});
			});

			private final HBox box = new HBox(8, btnEditar, btnBorrar);

			{
				box.setStyle("-fx-alignment: CENTER;");
				box.setFillHeight(false); // no fuerces a crecer en alto
				box.setSpacing(14);
//	            box.setTranslateY(-1.5);
				// Alternativa (más “top”):
//	             setAlignment(Pos.TOP_CENTER);
//	             setPadding(new Insets(0, 0, 6, 0)); // microajuste si quieres
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : box);
//	            btnBorrar.getStyleClass().add("delete");
				btnBorrar.getStyleClass().addAll("icon-btn", "danger");
			}
		});
	}

	private void setSceneProperties() {
		root.sceneProperty().addListener((obs, oldScene, scene) -> {
			if (scene == null || shortcutsInstalados)
				return; // 👈 evita dobles registros

			shortcutsInstalados = true;

			// ✔ Foco inicial para que funcionen atajos globales
			Platform.runLater(() -> root.requestFocus());

			// Helper para registrar atajos
			Runnable noop = () -> {
			};
			BiConsumer<KeyCombination, Runnable> accel = (kc, action) -> scene.getAccelerators().put(kc,
					action != null ? action : noop);

			// Foco filtros
			accel.accept(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> {
				txtFiltroTexto.requestFocus();
				txtFiltroTexto.selectAll();
			});
			accel.accept(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN), () -> {
				txtFiltroTags.requestFocus();
				txtFiltroTags.selectAll();
			});

			// Nueva
			accel.accept(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::abrirFormularioNueva);

			// Paginación
			accel.accept(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN), () -> {
				if (!btnAnterior.isDisabled()) {
					paginaActual = Math.max(1, paginaActual - 1);
					refrescarTabla();
				}
			});
			accel.accept(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN), () -> {
				if (!btnSiguiente.isDisabled()) {
					paginaActual = Math.min(totalPaginas, paginaActual + 1);
					refrescarTabla();
				}
			});
			accel.accept(new KeyCodeCombination(KeyCode.PAGE_UP), () -> {
				if (!btnAnterior.isDisabled()) {
					paginaActual = Math.max(1, paginaActual - 1);
					refrescarTabla();
				}
			});
			accel.accept(new KeyCodeCombination(KeyCode.PAGE_DOWN), () -> {
				if (!btnSiguiente.isDisabled()) {
					paginaActual = Math.min(totalPaginas, paginaActual + 1);
					refrescarTabla();
				}
			});

			// Abrir detalle / Editar (cuando la tabla tiene foco)
			accel.accept(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), () -> {
				if (table.isFocused() && table.getSelectionModel().getSelectedItem() != null) {
					abrirDetallePildora(table.getSelectionModel().getSelectedItem());
				}
			});
			accel.accept(new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN), () -> {
				if (table.isFocused() && table.getSelectionModel().getSelectedItem() != null) {
					abrirFormularioEditar(table.getSelectionModel().getSelectedItem());
				}
			});

			accel.accept(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
					() -> btnSoloFav.fire());

			accel.accept(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
					() -> btnAndOr.fire());

			scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() != KeyCode.DELETE)
					return;

				// 👇 si venimos de cerrar el detalle tras borrar, ignora 1 DELETE
				if (ignoreNextDelete) {
					ignoreNextDelete = false;
					e.consume();
					return;
				}

				if (root.getCenter() != table)
					return;
				Node focus = scene.getFocusOwner();
				boolean writing = (focus instanceof TextInputControl) || (focus instanceof WebView);
				if (writing)
					return;
				if (!table.isFocused())
					return;

				var sel = table.getSelectionModel().getSelectedItem();
				if (sel == null)
					return;

				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
						"¿Seguro que quieres eliminar la píldora \"" + sel.getTitulo() + "\"?");

				Dialogs.decorate(confirm, root);

				confirm.showAndWait().ifPresent(res -> {
					if (res.getButtonData().isDefaultButton()) {
						pildoraDao.eliminar(sel.getId());
						StatusBus.show("Píldora eliminada.", StatusBus.Type.INFO, Duration.seconds(3));
						int total = pildoraDao.contarFiltradas(
								txtFiltroTexto.getText().isBlank() ? null : txtFiltroTexto.getText().trim(),
								txtFiltroTags.getText().isBlank() ? null : txtFiltroTags.getText().trim(),
								btnAndOr.isSelected(), btnSoloFav.isSelected());
						totalPaginas = Math.max(1, (int) Math.ceil((double) total / TAMANIO_PAGINA));
						paginaActual = Math.min(paginaActual, totalPaginas);
						refrescarTabla();
					}
				});
				e.consume(); // no dejar que baje
			});

			// Esc: limpiar filtros
			scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() == KeyCode.ESCAPE) {
					if (txtFiltroTexto.isFocused() || txtFiltroTags.isFocused() || root.isFocused()) {
						txtFiltroTexto.clear();
						txtFiltroTags.clear();
						btnAndOr.setSelected(false);
						btnAndOr.setGraphic(new FontIcon(FontAwesomeSolid.CODE_BRANCH));
						paginaActual = 1;
						refrescarTabla();
						e.consume();
					}
				}
			});

			scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() == KeyCode.M && table.isFocused()) {
					var sel = table.getSelectionModel().getSelectedItem();
					if (sel != null) {
						boolean nueva = !sel.isFavorita();
						pildoraDao.marcarFavorita(sel.getId(), nueva);
						sel.setFavorita(nueva);
						StatusBus.show(nueva ? "Añadida a favoritas" : "Quitada de favoritas", StatusBus.Type.INFO,
								Duration.seconds(2));
						table.refresh();
						if (btnSoloFav.isSelected())
							refrescarTabla();
						e.consume();
					}
				}
			});

			scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				// Si no estamos en el listado (estás en editor/detalle), no pages
				if (uiOculta)
					return;

				Node focus = scene.getFocusOwner();
				boolean writing = focus instanceof TextInputControl || focus instanceof WebView; // evita robar
																									// PageUp/Down del
																									// WebView

				// Página anterior: PageUp o Ctrl+←
				if ((e.getCode() == KeyCode.PAGE_UP && !e.isShiftDown() && !e.isAltDown() && !e.isMetaDown())
						|| (e.isControlDown() && e.getCode() == KeyCode.LEFT)) {

					if (!writing && !btnAnterior.isDisabled()) {
						paginaActual = Math.max(1, paginaActual - 1);
						refrescarTabla();
						e.consume();
					}
					return;
				}

				// Página siguiente: PageDown o Ctrl+→
				if ((e.getCode() == KeyCode.PAGE_DOWN && !e.isShiftDown() && !e.isAltDown() && !e.isMetaDown())
						|| (e.isControlDown() && e.getCode() == KeyCode.RIGHT)) {

					if (!writing && !btnSiguiente.isDisabled()) {
						paginaActual = Math.min(totalPaginas, paginaActual + 1);
						refrescarTabla();
						e.consume();
					}
				}
			});
		});
	}

	private void abrirDetallePildora(Pildora p) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/detalle-pildora.fxml"));
			Parent detalleRoot = loader.load();
			DetallePildoraController controller = loader.getController();

			controller.setOnTagClick(tagName -> {
				// Cierra el detalle y vuelve al listado
				controller.dispose();
				restaurarFiltrosYPaginacion();

				// Aplica el filtro por ese tag (modo OR por defecto)
				txtFiltroTags.setText(tagName);
				btnAndOr.setSelected(false);
				btnAndOr.setGraphic(new FontIcon(FontAwesomeSolid.CODE_BRANCH));

				// Reinicia a página 1 y carga
				paginaActual = 1;
				refrescarTabla();

				// Foco de vuelta a la tabla
				table.requestFocus();

				// (opcional) feedback al usuario
				StatusBus.show("Filtrado por tag: " + tagName, StatusBus.Type.INFO, Duration.seconds(2));
			});

			ocultarFiltrosYPaginacion();
			root.setCenter(detalleRoot);
			controller.mostrarPildora(p);

			controller.setOnClose(() -> {
				controller.dispose(); // 👈 limpiar atajos del detalle
				restaurarFiltrosYPaginacion();
				refrescarTabla();
				table.requestFocus();
			});

			controller.setOnEdit(pildora -> {
				if (pildora.isProtegida()) {
					if (!unlockWithFeedback()) {
						StatusBus.show("Introduce el PIN para editar.", StatusBus.Type.WARN, Duration.seconds(3));
						return;
					}
				}
				controller.dispose();
				abrirFormularioEditar(pildora);
			});

			controller.setOnDelete(pildora -> {
				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
				confirm.setTitle("Confirmar borrado");
				confirm.setHeaderText("¿Seguro que quieres borrar esta píldora?");
				confirm.setContentText(pildora.getTitulo());

				Dialogs.decorate(confirm, root);

				confirm.showAndWait().ifPresent(res -> {
					if (res == ButtonType.OK) {
						pildoraDao.eliminar(p.getId());
						StatusBus.show("Píldora eliminada.", StatusBus.Type.INFO, Duration.seconds(3));
						controller.dispose(); // 👈 limpiar también aquí
						restaurarFiltrosYPaginacion();
						refrescarTabla();
					}
				});
			});

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void abrirFormularioEditar(Pildora p) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor-pildora.fxml"));
			Parent editorRoot = loader.load();

			ocultarFiltrosYPaginacion();
			root.setCenter(editorRoot);

			EditorPildoraController controller = loader.getController();
			controller.instalarAtajosEn(editorRoot); // 👈 atajos ligados al nodo del editor

			controller.setOnClose(() -> {
				restaurarFiltrosYPaginacion();
				refrescarTabla();
				table.requestFocus();
			});

			controller.cargarPildora(p);

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void abrirFormularioNueva() {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor-pildora.fxml"));
			Parent editorRoot = loader.load();

			ocultarFiltrosYPaginacion();
			root.setCenter(editorRoot);

			EditorPildoraController controller = loader.getController();
			controller.instalarAtajosEn(editorRoot); // 👈 igual aquí

			controller.setOnClose(() -> {
				restaurarFiltrosYPaginacion();
				refrescarTabla();
				table.requestFocus();
			});

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void cargarTabla(String tituloFiltro, String tagsFiltro) {
		boolean andMode = btnAndOr.isSelected();

		boolean soloFavoritas = btnSoloFav.isSelected();
		int total = pildoraDao.contarFiltradas(tituloFiltro, tagsFiltro, andMode, soloFavoritas);
		totalPaginas = Math.max(1, (int) Math.ceil((double) total / TAMANIO_PAGINA));

		// Ajusta paginaActual si se ha quedado fuera de rango
		if (paginaActual > totalPaginas) {
			paginaActual = totalPaginas;
		} else if (paginaActual < 1) {
			paginaActual = 1;
		}

		List<Pildora> todas = pildoraDao.listarFiltradas(tituloFiltro, tagsFiltro, andMode, soloFavoritas, paginaActual,
				TAMANIO_PAGINA, columnaOrden, direccionOrden);

		table.setItems(FXCollections.observableArrayList(todas));
		lblPagina.setText("Página " + paginaActual + " de " + totalPaginas);

		// Restaura el indicador visual del orden elegido por el usuario
		suppressSort = true;
		try {
			if (lastState == SortState.NONE || lastSortColumn == null) {
				table.getSortOrder().clear(); // sin flecha
			} else {
				if (table.getSortOrder().isEmpty() || table.getSortOrder().get(0) != lastSortColumn) {
					table.getSortOrder().setAll(lastSortColumn);
				}
				lastSortColumn.setSortType(
						lastState == SortState.ASC ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
			}
		} finally {
			suppressSort = false;
		}

		btnAnterior.setDisable(paginaActual == 1);
		btnSiguiente.setDisable(paginaActual == totalPaginas);

		table.refresh();
	}

	private void refrescarTabla() {
		String texto = txtFiltroTexto.getText().trim();
		String tags = txtFiltroTags.getText().trim();

		cargarTabla(texto.isEmpty() ? null : texto, tags.isEmpty() ? null : tags);
	}
	
	private void instalarWheelPagination() {
	    table.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
	        // Ignora gestos táctiles o modificadores (Ctrl = zoom del SO, etc.)
	        if (e.getTouchCount() > 0) return;
	        if (e.isControlDown() || e.isAltDown() || e.isShiftDown() || e.isMetaDown()) return;

	        // En trackpads puede haber scroll horizontal: prioriza el vertical
	        double dy = e.getDeltaY();
	        if (Math.abs(dy) < Math.abs(e.getDeltaX())) return;

	        // Throttle
	        long now = System.nanoTime();
	        if (now - lastWheelNanos < WHEEL_THROTTLE_NS) {
	            e.consume();
	            return;
	        }
	        lastWheelNanos = now;

	        // Rueda abajo -> siguiente página, rueda arriba -> anterior
	        if (dy < 0) {
	            if (btnSiguiente != null) btnSiguiente.fire();
	        } else if (dy > 0) {
	            if (btnAnterior != null) btnAnterior.fire();
	        }
	        e.consume();
	    });
	}


	private void ocultarFiltrosYPaginacion() {
		if (!uiOculta) {
			// Guarda el centro actual del listado (tabla/contenedor)
			centerBackup = root.getCenter();

			if (appHeader != null) {
				appHeader.setManaged(false);
				appHeader.setVisible(false);
			}
			paginationBox.setManaged(false);
			paginationBox.setVisible(false);
			uiOculta = true;
		}
	}

	private void restaurarFiltrosYPaginacion() {
		if (uiOculta) {
			if (appHeader != null) {
				appHeader.setManaged(true);
				appHeader.setVisible(true);
			}
			paginationBox.setManaged(true);
			paginationBox.setVisible(true);

			if (centerBackup != null) {
				root.setCenter(centerBackup); // vuelve el listado
			}

			uiOculta = false;

			// opcional, calidad de vida:
			table.requestFocus();
			refrescarTabla(); // si quieres refrescar al volver
		}
	}

	private Button iconButton(FontIcon icon, String tooltip, Runnable action) {
		icon.setIconSize(16);

		Button b = new Button();
		b.getStyleClass().add("icon-btn");
		b.setGraphic(icon);
		if (tooltip != null && !tooltip.isBlank())
			b.setTooltip(new Tooltip(tooltip));
		b.setOnAction(e -> {
			if (action != null)
				action.run();
		});
		return b;
	}

	private void setLogoFor(Theme t) {
		String path = (t == Theme.DARK) ? "/icons/gdg_W.png" : "/icons/gdg_B.png";
		var url = getClass().getResource(path);
		if (url != null) {
			imgLogo.setImage(new Image(url.toExternalForm(), 0, 64, true, true));
			imgLogo.setSmooth(true);
		}
	}

	private void initCustomTitleBar() {
		// Cerrar
		btnClose.setOnAction(e -> {
			Stage st = (Stage) root.getScene().getWindow();
			st.close();
		});

		// Arrastrar
		titleBar.setOnMousePressed(e -> {
			Stage st = (Stage) root.getScene().getWindow();
			dragOffsetX = e.getScreenX() - st.getX();
			dragOffsetY = e.getScreenY() - st.getY();
			e.consume();
		});

		titleBar.setOnMouseDragged(e -> {
			Stage st = (Stage) root.getScene().getWindow();
			st.setX(e.getScreenX() - dragOffsetX);
			st.setY(e.getScreenY() - dragOffsetY);
			e.consume();
		});

		// Evita que el botón “capture” el arrastre
		btnClose.setOnMousePressed(e -> e.consume());
		btnClose.setOnMouseDragged(e -> e.consume());
	}

	private void attachClearButton(CustomTextField tf, Runnable onCleared) {
		// Icono (Ikonli) en lugar de texto
		var icon = new FontIcon(FontAwesomeSolid.PLUS);
		icon.setIconSize(11); // 10–12 suele quedar bien
		icon.setRotate(45);

		var btn = new Button();
		btn.setGraphic(icon);
		btn.setText(null); // <- sin texto
		btn.setFocusTraversable(false);
		btn.setMnemonicParsing(false);
		btn.getStyleClass().add("clear-field");
		btn.setOnAction(e -> {
			if (!tf.getText().isBlank()) {
				tf.clear();
				if (onCleared != null)
					onCleared.run();
			}
		});

		// SLOT fijo (siempre presente)
		final double SLOT_W = 24; // 22–26 funciona bien; ajusta si quieres
		var slot = new StackPane(btn);
		slot.setMinWidth(SLOT_W);
		slot.setPrefWidth(SLOT_W);
		slot.setMaxWidth(SLOT_W);

		btn.setMinSize(18, 18);
		btn.setPrefSize(18, 18);
		btn.setMaxSize(18, 18);
		StackPane.setMargin(btn, new Insets(0.0, 0.0, 2.0, 0.0));

		tf.setRight(slot); // deja SIEMPRE el slot

		Runnable update = () -> {
			boolean show = tf.getText() != null && !tf.getText().isBlank();
			btn.setVisible(show);
			btn.setManaged(show);
		};
		tf.textProperty().addListener((o, a, b) -> update.run());
		update.run();
	}

	private void initSettingsMenu() {
		// Limpia y configura el botón (icono engranaje)
		btnSettings.getItems().clear();

		var gear = new FontIcon(FontAwesomeSolid.COG);
		btnSettings.setText(null);
		btnSettings.setGraphic(gear);
		btnSettings.setTooltip(new Tooltip("Ajustes"));
		btnSettings.getStyleClass().addAll("icon-btn", "primary");

		// --- Tipografías ---
		record Family(String label, String css) {
		}
		var families = List.of(new Family("System", "System"), new Family("Sans (Arial)", "Arial"),
				new Family("Serif", "Serif"), new Family("Monospace", "Consolas"));
		var miFamily = new Menu("Tipografía");
		var tgFamily = new ToggleGroup();
		String currentFamily = PREFS.get(PREF_FONT_FAMILY, DEF_FONT_FAMILY);
		for (var f : families) {
			var r = new RadioMenuItem(f.label());
			r.setToggleGroup(tgFamily);
			r.setSelected(f.css().equalsIgnoreCase(currentFamily));
			r.setOnAction(e -> {
				PREFS.put(PREF_FONT_FAMILY, f.css());
				applyTypographyNow();
			});
			miFamily.getItems().add(r);
		}

		// --- Tamaño letra ---
		var miSize = new Menu("Tamaño");
		var tgSize = new ToggleGroup();
		var sizes = List.of(new Family("Muy grande", "vlarge"), new Family("Grande", "large"),
				new Family("Normal", "normal"), new Family("Pequeño", "small"));
		String currentSize = PREFS.get(PREF_FONT_SIZE, DEF_FONT_SIZE);
		for (var item : sizes) {
			var r = new RadioMenuItem(item.label);
			r.setToggleGroup(tgSize);
			r.setSelected(item.css.equalsIgnoreCase(currentSize));
			r.setOnAction(e -> {
				PREFS.put(PREF_FONT_SIZE, item.css);
				applyTypographyNow();
			});
			miSize.getItems().add(r);
		}

		// --- Tema oscuro ---
		var miTemaOscuro = new CheckMenuItem("Tema oscuro");
		miTemaOscuro.setSelected(ThemeManager.load() == Theme.DARK);
		miTemaOscuro.setOnAction(e -> {
			var scene = root.getScene();
			if (scene == null)
				return;
			boolean wantsDark = miTemaOscuro.isSelected();
			boolean isDark = (ThemeManager.load() == Theme.DARK);
			if (wantsDark != isDark) {
				Theme t = ThemeManager.toggle(scene);
				setLogoFor(t);
				StatusBus.show(t == Theme.DARK ? "Tema oscuro" : "Tema claro", StatusBus.Type.INFO,
						javafx.util.Duration.seconds(2));
			}
		});

		// =============== Submenú Seguridad ===============
		var miSecurity = new Menu("Seguridad");

		// Crear/Cambiar PIN (texto dinámico)
		var miChangePin = new MenuItem(security.hasPin() ? "Cambiar PIN…" : "Crear PIN…");
		miChangePin.setOnAction(e -> {
			try {
				if (!security.hasPin()) {
					char[] np = PinDialogs.promptNewPin6(root);
					if (np == null)
						return;
					try {
						security.setupPin(np);
						StatusBus.show("PIN creado.", StatusBus.Type.SUCCESS, Duration.seconds(3));
						// refresca el menú para que el ítem pase a "Cambiar PIN…"
						initSettingsMenu();
					} finally {
						Arrays.fill(np, '\0');
					}
				} else {
					if (!ensureUnlockedWithRetries())
						return;
					char[] np = PinDialogs.promptNewPin6(root);
					if (np == null)
						return;
					try {
						security.changePin(null, np);
						StatusBus.show("PIN cambiado.", StatusBus.Type.SUCCESS, Duration.seconds(3));
					} finally {
						Arrays.fill(np, '\0');
					}
				}
			} catch (Exception ex) {
				StatusBus.show("No se pudo " + (security.hasPin() ? "cambiar" : "crear") + " el PIN.",
						StatusBus.Type.ERROR, Duration.seconds(4));
			}
		});

		// Configurar pregunta de seguridad…
		var miSetQuestion = new MenuItem("Configurar pregunta de seguridad…");
		miSetQuestion.setOnAction(e -> {
			try {
				if (!security.hasPin()) {
					StatusBus.show("Primero crea un PIN.", StatusBus.Type.WARN, Duration.seconds(3));
					return;
				}
				if (!ensureUnlockedWithRetries())
					return;
				showSetupSecurityQuestion(); // tu helper ya temado
			} catch (Exception ex) {
				StatusBus.show("No se pudo guardar la pregunta.", StatusBus.Type.ERROR, Duration.seconds(4));
			}
		});

		// Restablecer PIN con respuesta…
		var miResetByAnswer = new MenuItem("Restablecer PIN con respuesta…");
		miResetByAnswer.setOnAction(e -> {
			try {
				if (!security.hasSecurityQuestion()) {
					StatusBus.show("No hay pregunta de seguridad configurada.", StatusBus.Type.WARN,
							Duration.seconds(3));
					return;
				}
				if (security.isLockedOut()) {
					showLockoutModalFromListado();
					return;
				}

				String q = security.getSecurityQuestion();
				char[] ans = PinDialogs.promptAnswer(root, q);
				if (ans == null)
					return;
				char[] np = PinDialogs.promptNewPin6(root);
				if (np == null) {
					Arrays.fill(ans, '\0');
					return;
				}

				boolean ok;
				try {
					ok = security.resetPinWithAnswer(ans, np);
				} finally {
					Arrays.fill(ans, '\0');
					Arrays.fill(np, '\0');
				}

				if (ok) {
					StatusBus.show("PIN restablecido.", StatusBus.Type.SUCCESS, Duration.seconds(3));
					initSettingsMenu(); // por si cambia el estado del menú
				} else if (security.isLockedOut()) {
					showLockoutModalFromListado();
				} else {
					StatusBus.show("Respuesta incorrecta.", StatusBus.Type.ERROR, Duration.seconds(3));
				}
			} catch (Exception ex) {
				StatusBus.show("No se pudo restablecer el PIN.", StatusBus.Type.ERROR, Duration.seconds(4));
			}
		});

		// Generar código de recuperación…
		var miGenRecovery = new MenuItem("Generar código de recuperación…");
		miGenRecovery.setOnAction(e -> {
			try {
				if (!security.hasPin()) {
					StatusBus.show("Primero crea un PIN.", StatusBus.Type.WARN, Duration.seconds(3));
					return;
				}
				if (!ensureUnlockedWithRetries())
					return;
				showGenerateRecoveryCode(); // ya te muestra el código temado
			} catch (Exception ex) {
				StatusBus.show("No se pudo generar el código.", StatusBus.Type.ERROR, Duration.seconds(4));
			}
		});

		// Restablecer PIN con código de recuperación…
		var miResetByCode = new MenuItem("Restablecer PIN con código…");
		miResetByCode.setOnAction(e -> {
			try {
				if (!security.hasRecoveryCode()) {
					StatusBus.show("No hay código de recuperación activo.", StatusBus.Type.WARN, Duration.seconds(3));
					return;
				}
				if (security.isLockedOut()) {
					showLockoutModalFromListado();
					return;
				}

				String code = PinDialogs.promptRecoveryCode(root);
				if (code == null || code.isBlank())
					return;
				char[] np = PinDialogs.promptNewPin6(root);
				if (np == null)
					return;

				boolean ok;
				try {
					ok = security.resetPinWithRecoveryCode(code, np);
				} finally {
					Arrays.fill(np, '\0');
				}

				if (ok) {
					StatusBus.show("PIN restablecido con código.", StatusBus.Type.SUCCESS, Duration.seconds(3));
					initSettingsMenu();
				} else if (security.isLockedOut()) {
					showLockoutModalFromListado();
				} else {
					StatusBus.show("Código incorrecto o expirado.", StatusBus.Type.ERROR, Duration.seconds(3));
				}
			} catch (Exception ex) {
				StatusBus.show("No se pudo restablecer el PIN.", StatusBus.Type.ERROR, Duration.seconds(4));
			}
		});

		// Bloquear ahora
		var miLock = new MenuItem("Bloquear ahora");
		miLock.setOnAction(e -> {
			security.lockNow();
			StatusBus.show("Sesión bloqueada. Se pedirá PIN al acceder a contenido protegido.", StatusBus.Type.INFO,
					Duration.seconds(3));
		});

		miSecurity.getItems().addAll(miChangePin, new SeparatorMenuItem(), miSetQuestion, miResetByAnswer,
				new SeparatorMenuItem(), miGenRecovery, miResetByCode, new SeparatorMenuItem(), miLock);

		// --- Atajos ---
		var miShortcuts = new MenuItem("Atajos de teclado…");
		miShortcuts.setOnAction(e -> showShortcutsDialog());

		// Menú final (un único setAll)
		btnSettings.getItems().setAll(miFamily, miSize, new SeparatorMenuItem(), miTemaOscuro, new SeparatorMenuItem(),
				miSecurity, new SeparatorMenuItem(), miShortcuts);
	}

	// Modal de lockout igual que en editor, pero desde listado
	private void showLockoutModalFromListado() {

		long secs = Math.max(0L, (security.lockoutRemainingMillis() + 999) / 1000);
		Alert a = new Alert(Alert.AlertType.WARNING);
		a.setTitle("Intentos agotados");
		a.setHeaderText("Has excedido los intentos del PIN");
		a.setContentText("Podrás volver a intentarlo en " + secs + " segundo" + (secs == 1 ? "" : "s") + ".");
		Dialogs.decorate(a, root);
		a.showAndWait();
	}

	private void showSetupSecurityQuestion() {
		// Requiere sesión desbloqueada (necesitamos CMK en memoria)
		if (!ensureUnlockedWithRetries())
			return;

		Dialog<ButtonType> dlg = new Dialog<>();
		dlg.setTitle("Pregunta de seguridad");
		dlg.setHeaderText("Configura una pregunta y su respuesta para recuperar el PIN");
		Dialogs.decorate(dlg, root); // 👈 tu decorador para tema

		TextField txtQ = new TextField();
		txtQ.setPromptText("Ej: Nombre de tu primera mascota");

		PasswordField pfA = new PasswordField();
		pfA.setPromptText("Respuesta");

		PasswordField pfA2 = new PasswordField();
		pfA2.setPromptText("Repite la respuesta");

		VBox content = new VBox(8, new Label("Pregunta:"), txtQ, new Label("Respuesta:"), pfA,
				new Label("Confirmar respuesta:"), pfA2);
		content.setPrefWidth(420);
		dlg.getDialogPane().setContent(content);

		ButtonType OK = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
		ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		dlg.getDialogPane().getButtonTypes().setAll(OK, CANCEL);

		// Validación básica
		Node okBtn = dlg.getDialogPane().lookupButton(OK);
		okBtn.setDisable(true);
		Runnable validate = () -> {
			boolean valid = !txtQ.getText().isBlank() && !pfA.getText().isBlank()
					&& pfA.getText().equals(pfA2.getText());
			okBtn.setDisable(!valid);
		};
		txtQ.textProperty().addListener((o, a, b) -> validate.run());
		pfA.textProperty().addListener((o, a, b) -> validate.run());
		pfA2.textProperty().addListener((o, a, b) -> validate.run());
		Platform.runLater(txtQ::requestFocus);

		dlg.setResultConverter(bt -> bt);

		var res = dlg.showAndWait();
		if (res.isEmpty() || res.get() != OK)
			return;

		char[] ans = pfA.getText().toCharArray();
		try {
			security.setSecurityQuestion(txtQ.getText().trim(), ans);
			StatusBus.show("Pregunta de seguridad guardada.", StatusBus.Type.SUCCESS, Duration.seconds(3));
		} catch (Exception ex) {
			StatusBus.show("No se pudo guardar la pregunta: " + ex.getMessage(), StatusBus.Type.ERROR,
					Duration.seconds(4));
		} finally {
			Arrays.fill(ans, '\0');
			// No podemos vaciar los PasswordField internamente de forma segura, pero está
			// en memoria UI
		}
	}

	private void showGenerateRecoveryCode() {
		// Requiere sesión desbloqueada
		if (!ensureUnlockedWithRetries())
			return;

		String code;
		try {
			code = security.generateRecoveryCode();
		} catch (Exception ex) {
			StatusBus.show("No se pudo generar el código: " + ex.getMessage(), StatusBus.Type.ERROR,
					Duration.seconds(4));
			return;
		}

		// Muestra el código y opción de copiar
		Dialog<ButtonType> dlg = new Dialog<>();
		dlg.setTitle("Código de recuperación");
		dlg.setHeaderText("Guarda este código en un lugar seguro.\nTe permitirá restablecer el PIN si lo olvidas.");
		Dialogs.decorate(dlg, root);

		TextField txt = new TextField(code);
		txt.setEditable(false);
		txt.setStyle("-fx-font-family: 'Consolas', 'Menlo', 'Courier New', monospace; -fx-font-size: 14px;");
		Button btnCopy = new Button("Copiar");
		btnCopy.setOnAction(ev -> {
			ClipboardContent cc = new ClipboardContent();
			cc.putString(code);
			Clipboard.getSystemClipboard().setContent(cc);
			StatusBus.show("Código copiado al portapapeles.", StatusBus.Type.INFO, Duration.seconds(2));
		});

		VBox box = new VBox(12, new Label("Código de recuperación (mostrado una sola vez):"), new HBox(8, txt, btnCopy),
				new Label("⚠ Si lo pierdes y olvidas el PIN, no podrás recuperar el acceso."));
		box.setPrefWidth(480);
		dlg.getDialogPane().setContent(box);

		dlg.getDialogPane().getButtonTypes().setAll(new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
		dlg.showAndWait();
	}

	/** Aplica familia + tamaño guardados a toda la escena */
	private void applyTypographyNow() {
		Runnable apply = () -> {
			var scene = root.getScene();
			if (scene == null) {
				Platform.runLater(this::applyTypographyNow);
				return;
			}

			String family = PREFS.get(PREF_FONT_FAMILY, DEF_FONT_FAMILY);
			String sizeKey = PREFS.get(PREF_FONT_SIZE, DEF_FONT_SIZE);

			double px = switch (sizeKey) {
			case "small" -> 10.0;
			case "large" -> 18.0;
			case "vlarge" -> 22.0;
			default -> 14.0; // normal
			};

			// Aplica estilo inline al root de la escena (se hereda a todos los nodos)
			scene.getRoot().setStyle("""
					    -fx-font-family: '%s';
					    -fx-font-size: %spx;
					""".formatted(family.replace("'", "\\'"), px));
		};
		apply.run();
	}

	/** Diálogo con los atajos principales */
	private void showShortcutsDialog() {
	    var sb = new StringBuilder();

	    sb.append("LISTADO\n")
	      .append("───────\n")
	      .append("Rueda ratón                – Página anterior / siguiente\n")
	      .append("PageUp / Ctrl+←            – Página anterior\n")
	      .append("PageDown / Ctrl+→          – Página siguiente\n")
	      .append("Ctrl+F                     – Foco en filtro de texto\n")
	      .append("Ctrl+T                     – Foco en filtro de tags\n")
	      .append("Ctrl+N                     – Nueva píldora\n")
	      .append("Ctrl+O                     – Abrir detalle de la fila seleccionada\n")
	      .append("Ctrl+E                     – Editar la fila seleccionada\n")
	      .append("Supr                       – Eliminar la fila seleccionada\n")
	      .append("M                          – (Tabla enfocada) Alternar favorita\n")
	      .append("Ctrl+Shift+F               – Mostrar solo favoritas (toggle)\n")
	      .append("Ctrl+Shift+O               – Cambiar OR/AND para filtro de tags\n")
	      .append("Esc                        – Limpiar filtros (si hay foco en filtros) / Volver\n")
	      .append("Doble clic fila            – Abrir detalle\n\n")

	      .append("DETALLE\n")
	      .append("───────\n")
	      .append("Ctrl+E                     – Editar\n")
	      .append("Supr                       – Eliminar\n")
	      .append("Esc                        – Volver al listado\n")
	      .append("Click en tag               – Filtrar listado por ese tag\n\n")

	      .append("EDITOR\n")
	      .append("──────\n")
	      .append("Ctrl+S / Ctrl+Enter       – Guardar\n")
	      .append("Esc                       – Cancelar y volver\n")
	      .append("Ctrl+B                    – Insertar **negrita**\n")
	      .append("Ctrl+I                    – Insertar *cursiva*\n")
	      .append("Ctrl+K                    – Insertar [enlace](https://)\n")
	      .append("Ctrl+E                    – Insertar `código`\n")
	      .append("Tab / Shift+Tab           – Indentar / desindentar líneas (listas)\n")
	      .append("Vista previa              – Conmutador en la barra de herramientas\n")
	      .append("Enter / ','               – Confirmar tag en el campo de tags\n")
	      .append("Backspace (tags)          – Con input vacío, borrar el último tag\n\n")

	      .append("BORRADORES\n")
	      .append("──────────\n")
	      .append("Desde Ajustes → Borradores\n")
	      .append("  Nuevo borrador…         – Diálogo rápido\n")
	      .append("  Bandeja de borradores…  – Lista de borradores\n")
	      .append("Bandeja\n")
	      .append("  Doble clic fila         – Editar borrador\n")
	      .append("  Supr                    – Eliminar borrador seleccionado\n")
	      .append("Diálogo rápido de borrador\n")
	      .append("  Ctrl+S / Ctrl+Enter     – Guardar\n");


	    var alert = new Alert(Alert.AlertType.INFORMATION);
	    alert.setTitle("Atajos de teclado");
	    alert.setHeaderText(null);

	    var ta = new TextArea(sb.toString());
	    ta.setEditable(false);
	    ta.setWrapText(false);
	    ta.setFocusTraversable(false);
	    ta.setPrefColumnCount(56);
	    ta.setPrefRowCount(28);
	    ta.setStyle("-fx-font-family: 'Consolas','Monospaced'; -fx-font-size: 13px;");

	    alert.getDialogPane().setContent(ta);
	    alert.getDialogPane().setPrefWidth(620);

	    Dialogs.decorate(alert, root);
	    alert.showAndWait();
	}


	// DRAFTS:
	private void initDraftsUI() {
		if (btnBorradores == null) return;

	    // Icono
	    var eraser = new FontIcon(FontAwesomeSolid.ERASER);
	    eraser.setIconSize(16);

	    // Badge
	    draftsBadge = new Label();
	    draftsBadge.getStyleClass().add("badge");
	    draftsBadge.setVisible(false);
	    draftsBadge.setManaged(false); // no ocupa sitio cuando está oculto

	    // Wrapper 24x24 para que no recorte el badge
	    draftsIconStack = new StackPane(eraser, draftsBadge);
	    draftsIconStack.setMinSize(24, 24);
	    draftsIconStack.setPrefSize(24, 24);
	    draftsIconStack.setMaxSize(24, 24);

	    StackPane.setAlignment(eraser, Pos.CENTER);
	    StackPane.setAlignment(draftsBadge, Pos.TOP_RIGHT);
	    // SIN translate fuera del wrapper: mantenlo dentro
	    // Si quieres un pequeño ajuste interno:
	    draftsBadge.setTranslateX(8); // hacia dentro
	    draftsBadge.setTranslateY(-8);  // hacia abajo
	    draftsBadge.setMaxSize(16, 16);

	    btnBorradores.setText(null);
	    btnBorradores.setGraphic(draftsIconStack);
	    btnBorradores.setContentDisplay(ContentDisplay.GRAPHIC_ONLY); // asegura solo gráfico
	    btnBorradores.setTooltip(new Tooltip("Borradores"));
	    btnBorradores.getStyleClass().addAll("icon-btn");

		// Menú
		var miNuevo = new MenuItem("Nuevo borrador…");
		miNuevo.setOnAction(e -> {
			var res = DraftDialogs.showQuickDraftDialog(root, null, null, false);
			if (res == null)
				return;

			switch (res.action) {
			case SAVE -> {
				new DraftDao().insertar(res.draft);
				StatusBus.show("Borrador guardado.", StatusBus.Type.SUCCESS, javafx.util.Duration.seconds(2));
				refreshDraftBadge();
			}
			case CONVERT -> {
				// Convertir: abrir editor con prefill (y NO guardamos el borrador)
				openEditorPrefilled(res.draft.getTitulo(),
						// si viene protegido, ya pedimos PIN y desciframos antes de crear el borrador;
						// aquí res.draft.contenido está en claro si no es protegido (para diálogo
						// rápido)
						res.draft.isProtegida() ? tryDecryptDraftToString(res.draft) // seguridad
								: res.draft.getContenido(),
						res.draft.isProtegida());
			}
			default -> {
			}
			}
		});

		var miBandeja = new MenuItem("Bandeja de borradores…");
		miBandeja.setOnAction(e -> {
			DraftDialogs.showDraftsTray(root,
					// onEdit
					dft -> {
						// abre diálogo pre-rellenado y actualiza/convierte
						var plain = dft.isProtegida() ? tryDecryptDraftToString(dft) : dft.getContenido();
						var res = DraftDialogs.showQuickDraftDialog(root,
								dft.getTitulo(), plain, dft.isProtegida());
						if (res == null)
							return;

						switch (res.action) {
						case SAVE -> {
							// actualizar borrador existente (ojo: si cambio de protegido->no protegido y
							// viceversa)
							dft.setTitulo(res.draft.getTitulo());
							dft.setProtegida(res.draft.isProtegida());
							dft.setContenido(res.draft.getContenido());
							dft.setContenidoCipher(res.draft.getContenidoCipher());
							dft.setContenidoIv(res.draft.getContenidoIv());
							new DraftDao().actualizar(dft);
							StatusBus.show("Borrador actualizado.", StatusBus.Type.SUCCESS,
									javafx.util.Duration.seconds(2));
						}
						case CONVERT -> {
							openEditorPrefilled(res.draft.getTitulo(),
									res.draft.isProtegida() ? tryDecryptDraftToString(res.draft)
											: res.draft.getContenido(),
									res.draft.isProtegida());
							// elimina tras convertir
							new DraftDao().eliminar(dft.getId());
						}
						default -> {
						}
						}
						refreshDraftBadge();
					},
					// onConvert directo (desde la bandeja)
					dft -> {
						openEditorPrefilled(dft.getTitulo(),
								dft.isProtegida() ? tryDecryptDraftToString(dft) : dft.getContenido(),
								dft.isProtegida());
						refreshDraftBadge();
					},
					// onDelete (ya elimina por dentro, aquí solo feedback/extra lógica)
					dft -> StatusBus.show("Borrador eliminado.", StatusBus.Type.INFO, javafx.util.Duration.seconds(2)));
			refreshDraftBadge();
		});

		btnBorradores.getItems().setAll(miNuevo, miBandeja);

		// badge inicial
		refreshDraftBadge();
	}

	private String tryDecryptDraftToString(io.github.guillermo_david.model.Draft dft) {
		try {
			if (dft == null || !dft.isProtegida())
				return dft == null ? "" : (dft.getContenido() == null ? "" : dft.getContenido());
			if (!security.isUnlocked()) {
				if (!security
						.ensureUnlocked(() -> PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10)))) {
					return "";
				}
			}
			return security.decrypt(dft.getContenidoCipher(), dft.getContenidoIv());
		} catch (Exception ex) {
			return "";
		}
	}

	private void refreshDraftBadge() {
	    int count = new DraftDao().contar();

	    btnBorradores.setTooltip(new Tooltip("Borradores (" + count + ")"));

	    if (draftsBadge != null) {
	        if (count > 0) {
	            draftsBadge.setText(count > 99 ? "99+" : String.valueOf(count));
	            draftsBadge.setVisible(true);
	            draftsBadge.setManaged(true);
	        } else {
	            draftsBadge.setVisible(false);
	            draftsBadge.setManaged(false);
	        }
	    }
	}

	/**
	 * Abre el editor en modo NUEVA con datos precargados (título/cuerpo/proteger).
	 */
	private void openEditorPrefilled(String titulo, String cuerpo, boolean proteger) {
		try {
			var loader = new FXMLLoader(getClass().getResource("/fxml/editor-pildora.fxml"));
			Node rootEditor = loader.load();
			EditorPildoraController ctrl = loader.getController();

			// método nuevo en tu EditorPildoraController
			ctrl.setPrefill(titulo, cuerpo, proteger);

			ctrl.setOnClose(() -> {
				// al cerrar, volvemos al listado
				root.setCenter(table); // o restaurar como ya haces en tus flujos
				refrescarTabla();
			});

			root.setCenter(rootEditor);
		} catch (Exception ex) {
			ex.printStackTrace();
			StatusBus.show("No se pudo abrir el editor.", StatusBus.Type.ERROR, javafx.util.Duration.seconds(3));
		}
	}
}
