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
import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.Dialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.javafx.ThemeManager.Theme;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import io.github.guillermo_david.security.SecurityService;
import io.github.guillermo_david.util.AppVersion;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
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
	private java.util.Map<Integer, String> tagTextCache = java.util.Map.of();

	private int paginaActual = 1;
	private static final String PREF_PAGE_SIZE = "pageSize";
	private static final int[] PAGE_SIZES = {20, 50, 100, Integer.MAX_VALUE};
	private int tamanioPagina = 20;
	private int totalPaginas = 1;

	private boolean uiOculta = false;
	private boolean shortcutsInstalados = false;
	private boolean ignoreNextDelete = false;

	private static final String DEFAULT_ORDER_COL = "titulo";
	private static final String DEFAULT_ORDER_DIR = "ASC";
//	private static final String DEFAULT_ORDER_COL = "fecha_creacion";
//	private static final String DEFAULT_ORDER_DIR = "DESC";
	private String columnaOrden = DEFAULT_ORDER_COL;
	private String direccionOrden = DEFAULT_ORDER_DIR;
	private TableColumn<Pildora, ?> lastSortColumn = null;
	

	private SortState lastState = SortState.NONE;
	private boolean suppressSort = false;

	private static final Preferences PREFS = Preferences.userNodeForPackage(ListadoPildorasController.class);
	private static final String PREF_SOLO_FAV = "soloFavoritas";
	private static final String PREF_SHOW_HIDDEN = "mostrarOcultas";
	private static final String PREF_SIDEBAR_COLLAPSED = "sidebarCollapsed";

	private static final String PREF_FONT_FAMILY = "uiFontFamily";
	private static final String PREF_FONT_SIZE = "uiFontSize"; // "small" | "normal" | "large"
	private static final String DEF_FONT_FAMILY = "System";
	private static final String DEF_FONT_SIZE = "normal";
	private final int DEFAULT_SMALL_ICON_SIZE = 14;

	private double dragOffsetX, dragOffsetY;
	
	private SettingsMenuHelper settingsHelper;
	private TagSidebarHelper tagSidebarHelper;
	private DraftsUIHelper draftsHelper;


	String base = null;
	String light = null;
	String dark = null;

	private Node centerBackup;

	@FXML private BorderPane root;
	@FXML private SplitMenuButton btnNueva;
	@FXML private Button btnAnterior, btnSiguiente, btnClose;
	@FXML private ToggleButton btnSoloFav;
	@FXML private MenuButton btnSettings;
	@FXML private HBox paginationBox, statusBar, titleBar, appHeader;
	@FXML private Label lblPagina, lblStatus;
	@FXML private TableView<Pildora> table;
	@FXML private TableColumn<Pildora, String> colTitulo, colDescripcion, colTags;
	@FXML private TableColumn<Pildora, Void> colFav, colAcciones;
	@FXML private CustomTextField txtFiltroTexto;
	@FXML private ImageView imgLogo;
	@FXML private VBox tagSidebar, tagListBox;
	@FXML private Button btnToggleSidebar;
	private boolean mostrarOcultas = false;

	@FXML
	public void initialize() {

		base = MainApp.class.getResource("/css/base.css").toExternalForm();
		light = MainApp.class.getResource("/css/theme-light.css").toExternalForm();
		dark = MainApp.class.getResource("/css/theme-dark.css").toExternalForm();

		setStatusBar();
		addVersionBadge();
		setTableProperties();
		setColFav();
		setColTitulo();
		setColDescripcion();
		setColTags();
		setColAcciones();
		// Restore saved toggle states before setBotones() wires them up
		mostrarOcultas = PREFS.getBoolean(PREF_SHOW_HIDDEN, false);
		tamanioPagina = PREFS.getInt(PREF_PAGE_SIZE, 20);
		setBotones();
		btnSoloFav.setSelected(PREFS.getBoolean(PREF_SOLO_FAV, false));

		settingsHelper = new SettingsMenuHelper(root, security, btnSettings, PREFS,
				PREF_FONT_FAMILY, PREF_FONT_SIZE, DEF_FONT_FAMILY, DEF_FONT_SIZE,
				this::ensureUnlockedWithRetries, this::refrescarTabla,
				this::setLogoFor, p -> paginaActual = p);
		settingsHelper.initSettingsMenu();
		settingsHelper.applyTypographyNow();
		cargarTabla(null, null);
		setLogoFor(ThemeManager.load());
		setTxtFiltros();
		setSceneProperties();
//	    initThemeToggle();
//	    syncThemeToggleWithCurrent();
//	    hookLogoToTheme();

		// Panel lateral de tags
		tagSidebarHelper = new TagSidebarHelper(tagSidebar, tagListBox, btnToggleSidebar,
				() -> { paginaActual = 1; refrescarTabla(); },
				() -> mostrarOcultas, PREFS);
		tagSidebarHelper.init();

		// Barra de título custom
		initCustomTitleBar();

		if (centerBackup == null) {
			centerBackup = root.getCenter(); // guarda el "centro del listado" inicial
		}

	}

	private void setTxtFiltros() {
		txtFiltroTexto.setOnAction(e -> {
			paginaActual = 1;
			refrescarTabla();
		});

		txtFiltroTexto.setTooltip(new Tooltip("Ctrl+F"));

		attachClearButton(txtFiltroTexto, () -> {
			paginaActual = 1;
			refrescarTabla();
		});

		PauseTransition debounce = new PauseTransition(Duration.millis(300));
		debounce.setOnFinished(e -> {
			paginaActual = 1;
			refrescarTabla();
		});
		txtFiltroTexto.textProperty().addListener((obs, old, val) -> debounce.playFromStart());

		txtFiltroTexto.setPrefWidth(275);
		txtFiltroTexto.setMinWidth(200);
		txtFiltroTexto.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(txtFiltroTexto, Priority.ALWAYS);
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
	
	private void addVersionBadge() {
	    // Coffee button (left side)
	    var coffeeIcon = new FontIcon(FontAwesomeSolid.MUG_HOT);
	    coffeeIcon.setIconSize(14);
	    coffeeIcon.getStyleClass().add("coffee-icon");
	    var coffeeBtn = new Button();
	    coffeeBtn.setGraphic(coffeeIcon);
	    coffeeBtn.getStyleClass().addAll("icon-btn", "coffee-btn");
	    coffeeBtn.setTooltip(new Tooltip("Invite me a coffee!"));
	    coffeeBtn.setOnAction(e -> {
	        try {
	            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://ko-fi.com/guillermo_david"));
	        } catch (Exception ignored) {}
	    });
	    statusBar.getChildren().add(0, coffeeBtn);

	    HBox.setHgrow(lblStatus, Priority.ALWAYS);
	    lblStatus.setMaxWidth(Double.MAX_VALUE);

	    String v = AppVersion.get();
	    Label lblVer = new Label("v" + v);
	    lblVer.getStyleClass().add("version-badge");
	    lblVer.setOpacity(0.8);
	    statusBar.getChildren().add(lblVer);
	}

	@SuppressWarnings("unchecked")
	private void setTableProperties() {
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setFixedCellSize(30); // alto por fila
		var emptyIcon = new FontIcon(FontAwesomeSolid.BOX_OPEN);
		emptyIcon.setIconSize(32);
		emptyIcon.getStyleClass().add("muted-icon");
		var emptyLabel = new Label("No se encontraron píldoras");
		emptyLabel.getStyleClass().add("empty-state-label");
		var emptyHint = new Label("Prueba a cambiar los filtros o crea una nueva");
		emptyHint.getStyleClass().add("empty-state-hint");
		var emptyBtn = new Button("Crear píldora");
		emptyBtn.getStyleClass().add("btn-accent-flat");
		emptyBtn.setOnAction(e -> abrirFormularioNueva());
		var emptyBox = new VBox(8, emptyIcon, emptyLabel, emptyHint, emptyBtn);
		emptyBox.setAlignment(Pos.CENTER);
		emptyBox.getStyleClass().add("empty-state-box");
		table.setPlaceholder(emptyBox);

		colTitulo.setSortable(true);
		colDescripcion.setSortable(true);
		colTags.setSortable(false);
		colFav.setSortable(false);
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
				if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
					Pildora p = row.getItem();
					if (p.isProtegida() && !security.isUnlocked()) {
						if (!ensureUnlockedWithRetries()) {
							return;
						}
					}
					abrirDetallePildora(p);
				}
			});

			ContextMenu ctx = new ContextMenu();
			ctx.getItems().add(new MenuItem("..."));

			ctx.setOnShowing(e -> {
				ctx.getItems().clear();
				Pildora p = row.getItem();
				if (p == null) return;

				var miAbrir = new MenuItem("Abrir");
				miAbrir.setGraphic(new FontIcon(FontAwesomeSolid.EYE));
				miAbrir.setOnAction(ev -> {
					if (p.isProtegida() && !security.isUnlocked() && !ensureUnlockedWithRetries()) return;
					abrirDetallePildora(p);
				});

				var miEditar = new MenuItem("Editar");
				miEditar.setGraphic(new FontIcon(FontAwesomeRegular.EDIT));
				miEditar.setOnAction(ev -> {
					if (p.isProtegida() && !security.isUnlocked() && !ensureUnlockedWithRetries()) return;
					abrirFormularioEditar(p);
				});

				var miFav = new MenuItem(p.isFavorita() ? "Quitar de favoritas" : "Añadir a favoritas");
				miFav.setGraphic(new FontIcon(p.isFavorita() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR));
				miFav.setOnAction(ev -> {
					boolean nueva = !p.isFavorita();
					pildoraDao.marcarFavorita(p.getId(), nueva);
					p.setFavorita(nueva);
					StatusBus.show(nueva ? "Añadida a favoritas" : "Quitada de favoritas",
							StatusBus.Type.INFO, Duration.seconds(2));
					table.refresh();
					if (btnSoloFav.isSelected()) refrescarTabla();
				});

				var miPin = new MenuItem(p.isPinned() ? "Desfijar" : "Fijar arriba");
				miPin.setGraphic(new FontIcon(FontAwesomeSolid.THUMBTACK));
				miPin.setOnAction(ev -> {
					boolean nuevo = !p.isPinned();
					pildoraDao.marcarPinned(p.getId(), nuevo);
					p.setPinned(nuevo);
					StatusBus.show(nuevo ? "Píldora anclada" : "Píldora desanclada",
							StatusBus.Type.INFO, Duration.seconds(2));
					refrescarTabla();
				});

				var miOcultar = new MenuItem(p.isOculta() ? "Mostrar" : "Ocultar");
				miOcultar.setGraphic(new FontIcon(p.isOculta() ? FontAwesomeSolid.EYE : FontAwesomeSolid.EYE_SLASH));
				miOcultar.setOnAction(ev -> {
					boolean nuevo = !p.isOculta();
					pildoraDao.marcarOculta(p.getId(), nuevo);
					p.setOculta(nuevo);
					StatusBus.show(nuevo ? "Píldora oculta" : "Píldora visible",
							StatusBus.Type.INFO, Duration.seconds(2));
					refrescarTabla();
				});

				var miEliminar = new MenuItem("Eliminar");
				miEliminar.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
				miEliminar.getStyleClass().add("danger");
				miEliminar.setOnAction(ev -> {
					if (p.isProtegida() && !security.isUnlocked() && !ensureUnlockedWithRetries()) return;
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

				ctx.getItems().addAll(
						miAbrir, miEditar,
						new SeparatorMenuItem(),
						miFav, miPin,
						new SeparatorMenuItem(),
						miOcultar,
						new SeparatorMenuItem(),
						miEliminar);
			});

			row.itemProperty().addListener((obs, oldItem, newItem) -> {
				row.setContextMenu(newItem != null ? ctx : null);
			});

			return row;
		});

		colFav.setMinWidth(80);
		colFav.setPrefWidth(80);
		colFav.setMaxWidth(80);
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
			// PIN temado + foco + "recordar 10 min"
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

				// Pin button
				Button pinBtn = new Button();
				pinBtn.getStyleClass().add("pin");
				FontIcon pinIcon = new FontIcon(FontAwesomeSolid.THUMBTACK);
				pinIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);
				pinIcon.getStyleClass().add("pin-icon");
				if (p.isPinned()) pinIcon.getStyleClass().add("pin-active");
				pinIcon.setRotate(p.isPinned() ? -20 : 0);
				pinBtn.setGraphic(pinIcon);
				pinBtn.setTooltip(new Tooltip(p.isPinned() ? "Desfijar" : "Fijar"));
				pinBtn.setOnAction(e -> {
					boolean nuevo = !p.isPinned();
					pildoraDao.marcarPinned(p.getId(), nuevo);
					p.setPinned(nuevo);
					pinIcon.getStyleClass().remove("pin-active");
					if (nuevo) pinIcon.getStyleClass().add("pin-active");
					pinIcon.setRotate(nuevo ? -20 : 0);
					pinBtn.setTooltip(new Tooltip(nuevo ? "Desfijar" : "Fijar"));
					StatusBus.show(nuevo ? "Píldora anclada" : "Píldora desanclada", StatusBus.Type.INFO, Duration.seconds(2));
					ListadoPildorasController.this.refrescarTabla();
				});

				// Star button
				Button star = new Button();
				star.getStyleClass().add("star");
				FontIcon starIcon = new FontIcon(p.isFavorita() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
				starIcon.getStyleClass().add("star-icon");
				starIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);
				if (p.isFavorita()) starIcon.getStyleClass().add("star-fav");
				star.setGraphic(starIcon);
				star.setOnAction(e -> {
					boolean nueva = !p.isFavorita();
					pildoraDao.marcarFavorita(p.getId(), nueva);
					p.setFavorita(nueva);
					starIcon.setIconCode(nueva ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
					starIcon.getStyleClass().remove("star-fav");
					if (nueva) starIcon.getStyleClass().add("star-fav");
					StatusBus.show(nueva ? "Añadida a favoritas" : "Quitada de favoritas", StatusBus.Type.INFO, Duration.seconds(2));
					if (btnSoloFav.isSelected()) refrescarTabla();
				});

				HBox box = new HBox(4, pinBtn, star);
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

		// --- MOSTRAR/OCULTAR ARCHIVADAS ---
		var btnOcultas = new ToggleButton();
		FontIcon eyeIcon = new FontIcon(FontAwesomeSolid.EYE_SLASH);
		eyeIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);
		btnOcultas.getStyleClass().setAll("round-toggle", "eye-toggle");
		btnOcultas.setGraphic(eyeIcon);
		btnOcultas.setText(null);
		btnOcultas.setTooltip(new Tooltip("Mostrar/ocultar archivadas"));
		btnOcultas.setSelected(mostrarOcultas);
		eyeIcon.setIconCode(mostrarOcultas ? FontAwesomeSolid.EYE : FontAwesomeSolid.EYE_SLASH);
		btnOcultas.selectedProperty().addListener((obs, old, sel) -> {
			mostrarOcultas = sel;
			PREFS.putBoolean(PREF_SHOW_HIDDEN, sel);
			eyeIcon.setIconCode(sel ? FontAwesomeSolid.EYE : FontAwesomeSolid.EYE_SLASH);
			paginaActual = 1;
			refrescarTabla();
			StatusBus.show(sel ? "Mostrando ocultas: activado" : "Mostrando ocultas: desactivado",
					StatusBus.Type.INFO, Duration.seconds(2));
		});
		// Insert toggle into header after btnSoloFav
		if (appHeader != null) {
			int favIdx = appHeader.getChildren().indexOf(btnSoloFav);
			if (favIdx >= 0) {
				appHeader.getChildren().add(favIdx + 1, btnOcultas);
			}
		}

		FontIcon nuevaIcon = new FontIcon(FontAwesomeSolid.PLUS);
		nuevaIcon.setIconSize(14);
		btnNueva.setGraphic(nuevaIcon);
		btnNueva.setText(null);
		btnNueva.setTooltip(new Tooltip("Nueva píldora (Ctrl+N)"));
		btnNueva.setOnAction(e -> abrirFormularioNueva());
		btnNueva.getStyleClass().add("btn-accent");

		draftsHelper = new DraftsUIHelper(root, security, btnNueva,
				this::refrescarTabla, () -> table, this::abrirFormularioNueva);
		draftsHelper.init();

		var prevIcon = new FontIcon(FontAwesomeSolid.CHEVRON_LEFT);
		prevIcon.setIconSize(12);
		btnAnterior.setGraphic(prevIcon);
		btnAnterior.setText(null);
		btnAnterior.getStyleClass().addAll("icon-btn", "pagination-arrow");
		btnAnterior.setTooltip(new Tooltip("Página anterior (Ctrl+← / PageUp)"));
		btnAnterior.setOnAction(e -> {
			if (paginaActual > 1) {
				paginaActual--;
				refrescarTabla();
			}
		});

		var nextIcon = new FontIcon(FontAwesomeSolid.CHEVRON_RIGHT);
		nextIcon.setIconSize(12);
		btnSiguiente.setGraphic(nextIcon);
		btnSiguiente.setText(null);
		btnSiguiente.getStyleClass().addAll("icon-btn", "pagination-arrow");
		btnSiguiente.setTooltip(new Tooltip("Página siguiente (Ctrl+→ / PageDown)"));
		btnSiguiente.setOnAction(e -> {
			if (paginaActual < totalPaginas) {
				paginaActual++;
				refrescarTabla();
			}
		});

		buildPageSizeSelector();
	}

	private javafx.scene.control.MenuButton pageSizeMenu;

	private void buildPageSizeSelector() {
		String currentLabel = tamanioPagina == Integer.MAX_VALUE ? "Todo" : String.valueOf(tamanioPagina);
		pageSizeMenu = new javafx.scene.control.MenuButton(currentLabel);
		pageSizeMenu.getStyleClass().add("page-size-menu");
		pageSizeMenu.setTooltip(new Tooltip("Píldoras por página"));

		for (int size : PAGE_SIZES) {
			String label = size == Integer.MAX_VALUE ? "Todo" : String.valueOf(size);
			var mi = new javafx.scene.control.MenuItem(label);
			final int s = size;
			mi.setOnAction(e -> applyPageSize(s));
			pageSizeMenu.getItems().add(mi);
		}

		paginationBox.getChildren().add(pageSizeMenu);
	}

	private void applyPageSize(int size) {
		tamanioPagina = size;
		PREFS.putInt(PREF_PAGE_SIZE, size);
		paginaActual = 1;
		pageSizeMenu.setText(size == Integer.MAX_VALUE ? "Todo" : String.valueOf(size));
		refrescarTabla();
	}

	private void setColTitulo() {
		colTitulo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTitulo()));
		colTitulo.setMaxWidth(200);
		colTitulo.setCellFactory(col -> new TableCell<>() {
			private final Label lbl = new Label();
			private final Tooltip tt = new Tooltip();
			{
				lbl.setWrapText(false);
				lbl.setMaxWidth(Double.MAX_VALUE);
				lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
				lbl.setTooltip(tt);
			}

			@Override
			protected void updateItem(String value, boolean empty) {
				super.updateItem(value, empty);
				if (empty || value == null) {
					setGraphic(null);
				} else {
					lbl.setText(value);
					tt.setText(value);
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
					lbl.setTooltip(null);
					setGraphic(wrap);
				} else {
					lbl.setText(value);
					if (value != null && !value.isBlank()) {
						var existing = lbl.getTooltip();
						if (existing != null) {
							existing.setText(value);
						} else {
							lbl.setTooltip(new Tooltip(value));
						}
					} else {
						lbl.setTooltip(null);
					}
					setGraphic(lbl);
				}
			}
		});
	}

	private void setColTags() {
		colTags.setCellValueFactory(cell -> {
			String cached = tagTextCache.getOrDefault(cell.getValue().getId(), "");
			return new SimpleStringProperty(cached);
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
				// Alternativa (más "top"):
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
						String delTags = tagSidebarHelper != null ? tagSidebarHelper.getSelectedTags() : null;
						int total = pildoraDao.contarFiltradas(
								txtFiltroTexto.getText().isBlank() ? null : txtFiltroTexto.getText().trim(),
								delTags,
								true, btnSoloFav.isSelected(), mostrarOcultas);
						totalPaginas = Math.max(1, (int) Math.ceil((double) total / tamanioPagina));
						paginaActual = Math.min(paginaActual, totalPaginas);
						refrescarTabla();
					}
				});
				e.consume(); // no dejar que baje
			});

			// Esc: limpiar filtros
			scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() == KeyCode.ESCAPE) {
					if (txtFiltroTexto.isFocused() || root.isFocused()) {
						txtFiltroTexto.clear();
						if (tagSidebarHelper != null) tagSidebarHelper.clearSelection();
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

				// Aplica el filtro por ese tag
				if (tagSidebarHelper != null) tagSidebarHelper.selectTag(tagName);

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
			
			controller.setOnOpenPildoraId((Integer id) -> {
			    // Cierra actual, restaura y abre el nuevo detalle
			    // (o navega directamente sin restaurar si prefieres comportamiento "pila")
			    Pildora destino = pildoraDao.buscarPorId(id);
			    if (destino != null) {
			        controller.dispose();
			        // reutiliza tu mismo flujo de abrir detalle:
			        abrirDetallePildora(destino);
			    }
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
		boolean andMode = true;

		boolean soloFavoritas = btnSoloFav.isSelected();
		int total = pildoraDao.contarFiltradas(tituloFiltro, tagsFiltro, andMode, soloFavoritas, mostrarOcultas);
		totalPaginas = Math.max(1, (int) Math.ceil((double) total / tamanioPagina));

		// Ajusta paginaActual si se ha quedado fuera de rango
		if (paginaActual > totalPaginas) {
			paginaActual = totalPaginas;
		} else if (paginaActual < 1) {
			paginaActual = 1;
		}

		List<Pildora> todas = pildoraDao.listarFiltradas(tituloFiltro, tagsFiltro, andMode, soloFavoritas, paginaActual,
				tamanioPagina, columnaOrden, direccionOrden, mostrarOcultas);

		var ids = todas.stream().map(Pildora::getId).collect(Collectors.toList());
		tagDao.preloadForPildoras(ids);
		var map = new java.util.HashMap<Integer, String>(ids.size() * 2);
		for (Pildora p : todas) {
			List<Tag> tags = tagDao.findByPildoraId(p.getId());
			map.put(p.getId(), tags.stream().map(Tag::getNombre).collect(Collectors.joining(", ")));
		}
		tagTextCache = map;
		table.setItems(FXCollections.observableArrayList(todas));

		int rowCount = todas.size();
		double cellHeight = table.getFixedCellSize();
		javafx.application.Platform.runLater(() -> {
			double headerH = 0;
			var headerNode = table.lookup(".column-header-background");
			if (headerNode != null) {
				headerH = headerNode.getBoundsInLocal().getHeight();
			}
			if (headerH <= 0) headerH = 30;
			var insets = table.getInsets();
			double insetsV = (insets != null) ? insets.getTop() + insets.getBottom() : 0;
			double exact = headerH + rowCount * cellHeight + insetsV;
			table.setPrefHeight(exact);
			table.setMinHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
			table.setMaxHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
		});

		boolean singlePage = (tamanioPagina == Integer.MAX_VALUE || totalPaginas <= 1);
		if (total == 0) {
			lblPagina.setText("0 de 0");
		} else if (singlePage) {
			lblPagina.setText(total + (total == 1 ? " píldora" : " píldoras"));
		} else {
			int desde = (paginaActual - 1) * tamanioPagina + 1;
			int hasta = Math.min(paginaActual * tamanioPagina, total);
			lblPagina.setText(String.format("%d - %d de %d", desde, hasta, total));
		}
		btnAnterior.setVisible(!singlePage);
		btnAnterior.setManaged(!singlePage);
		btnSiguiente.setVisible(!singlePage);
		btnSiguiente.setManaged(!singlePage);

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

	private boolean refreshingSidebar = false;

	private void refrescarTabla() {
		TagDao.invalidateCache();
		String texto = txtFiltroTexto.getText().trim();
		String tags = tagSidebarHelper != null ? tagSidebarHelper.getSelectedTags() : null;

		cargarTabla(texto.isEmpty() ? null : texto, tags);
		if (tagSidebarHelper != null && !refreshingSidebar) {
			refreshingSidebar = true;
			try { tagSidebarHelper.refresh(); } finally { refreshingSidebar = false; }
		}
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

		// Evita que el botón "capture" el arrastre
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

}
