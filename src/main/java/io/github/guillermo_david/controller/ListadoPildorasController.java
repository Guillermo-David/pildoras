package io.github.guillermo_david.controller;

import java.io.IOException;
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
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.javafx.ThemeManager;
import io.github.guillermo_david.javafx.ThemeManager.Theme;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ListadoPildorasController {
	
	private enum SortState { ASC, DESC, NONE }

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
	
	private SortState lastState = SortState.NONE;
	private boolean suppressSort = false;

	private static final Preferences PREFS = Preferences.userNodeForPackage(ListadoPildorasController.class);
	private static final String PREF_SOLO_FAV = "soloFavoritas";
	private static final String PREF_AND_OR = "filtroAndOr";
	
	private static final String PREF_FONT_FAMILY = "uiFontFamily";
	private static final String PREF_FONT_SIZE   = "uiFontSize";   // "small" | "normal" | "large"
	private static final String DEF_FONT_FAMILY = "System";
	private static final String DEF_FONT_SIZE   = "normal";
	private final int DEFAULT_SMALL_ICON_SIZE = 14;

	private double dragOffsetX, dragOffsetY;
	
	String base = null;
    String light = null;
    String dark  = null;

	private Node centerBackup;

	@FXML private BorderPane root;
	@FXML private Button btnNueva, btnAnterior, btnSiguiente, btnClose;
	@FXML private ToggleButton btnAndOr, btnSoloFav;
	@FXML private MenuButton btnSettings;
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
	    dark  = MainApp.class.getResource("/css/theme-dark.css").toExternalForm();
	    
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
	        centerBackup = root.getCenter();  // guarda el “centro del listado” inicial
	    }

	}

	private void setTxtFiltros() {
		txtFiltroTexto.setOnAction(e -> { paginaActual = 1; refrescarTabla(); });
		txtFiltroTags.setOnAction(e -> { paginaActual = 1; refrescarTabla(); });
		
		txtFiltroTexto.setTooltip(new Tooltip("Ctrl+F"));
		txtFiltroTags.setTooltip(new Tooltip("Ctrl+T"));
		
		attachClearButton(txtFiltroTexto, () -> { paginaActual = 1; refrescarTabla(); });
	    attachClearButton(txtFiltroTags,  () -> { paginaActual = 1; refrescarTabla(); });
	    
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
		    if (newSkin == null) return;
		    Platform.runLater(() -> {
		        Region headerBg = (Region) table.lookup(".column-header-background");
		        var headerHeight = headerBg != null
		                ? headerBg.heightProperty()
		                : new SimpleDoubleProperty(28);

		        double row = table.getFixedCellSize(); // 30
		        int rows   = TAMANIO_PAGINA;           // 20

		        table.prefHeightProperty().unbind();
		        table.prefHeightProperty().bind(headerHeight.add(rows * row + 2));

		        table.setMinHeight(Region.USE_COMPUTED_SIZE);
		        table.setMaxHeight(Region.USE_COMPUTED_SIZE);

		        var wnd = table.getScene() != null ? table.getScene().getWindow() : null;
		        if (wnd instanceof Stage st) Platform.runLater(st::sizeToScene);
		    });
		});
		
		colTitulo.setSortable(true);
		colDescripcion.setSortable(true);
		colTags.setSortable(false);
		colFav.setSortable(false);
		colPinned.setSortable(false);
		colAcciones.setSortable(false);

		table.setSortPolicy(tv -> {
		    if (suppressSort) return true;

		    TableColumn<Pildora, ?> primary =
		            tv.getSortOrder().isEmpty() ? null : tv.getSortOrder().get(0);

		    // --- 3er click: JavaFX ya limpió sortOrder (primary == null). Forzamos NONE + orden por defecto.
		    if (primary == null && lastSortColumn != null && lastState != SortState.NONE) {
		        suppressSort = true;
		        try {
		            tv.getSortOrder().clear();   // sin flecha
		            lastSortColumn = null;
		            lastState = SortState.NONE;
		        } finally {
		            suppressSort = false;
		        }
		        columnaOrden   = DEFAULT_ORDER_COL;  // "fecha_creacion"
		        direccionOrden = DEFAULT_ORDER_DIR;  // "DESC"
		        paginaActual = 1;
		        refrescarTabla();
		        return true; // ya gestionado
		    }

		    if (primary == null) return true; // no hay transición (p.ej. click fuera), no hacer nada

		    SortState next;
		    if (primary == lastSortColumn) {
		        next = switch (lastState) {
		            case ASC  -> SortState.DESC;
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

		            columnaOrden   = DEFAULT_ORDER_COL;
		            direccionOrden = DEFAULT_ORDER_DIR;

		            paginaActual = 1;
		            refrescarTabla();
		        } else {
		            if (tv.getSortOrder().isEmpty() || tv.getSortOrder().get(0) != primary) {
		                tv.getSortOrder().setAll(primary);
		            }
		            primary.setSortType(next == SortState.ASC
		                    ? TableColumn.SortType.ASCENDING
		                    : TableColumn.SortType.DESCENDING);

		            if (primary == colTitulo) {
		                columnaOrden = "titulo";
		            } else if (primary == colDescripcion) {
		                columnaOrden = "descripcion";
		            } else {
		                tv.getSortOrder().clear();
		                lastSortColumn = null;
		                lastState = SortState.NONE;
		                columnaOrden   = DEFAULT_ORDER_COL;
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
	            if (p.isPinned()) icon.getStyleClass().add("pin-active");
	            icon.setRotate(p.isPinned() ? -20 : 0); // opcional: efecto clavada

	            pinBtn.setGraphic(icon);
	            pinBtn.setTooltip(new Tooltip(p.isPinned() ? "Desfijar" : "Fijar"));

	            pinBtn.setOnAction(e -> {
	                boolean nuevo = !p.isPinned();
	                pildoraDao.marcarPinned(p.getId(), nuevo);
	                p.setPinned(nuevo);

	                icon.getStyleClass().remove("pin-active");
	                if (nuevo) icon.getStyleClass().add("pin-active");
	                icon.setRotate(nuevo ? -20 : 0);
	                pinBtn.setTooltip(new Tooltip(nuevo ? "Desfijar" : "Fijar"));

	                StatusBus.show(nuevo ? "Píldora anclada" : "Píldora desanclada",
	                        StatusBus.Type.INFO, Duration.seconds(2));

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
		FontIcon favTopIcon = new FontIcon(btnSoloFav.isSelected()
		        ? FontAwesomeSolid.STAR
		        : FontAwesomeRegular.STAR);
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
		    
		    StatusBus.show(
		            sel ? "Mostrando solo favoritas" : "Mostrando todas las píldoras",
		            StatusBus.Type.INFO,
		            Duration.seconds(2)
		        );
		});

		// --- AND / OR ---
		FontIcon btnAndOrIcon = new FontIcon(btnAndOr.isSelected()
		        ? FontAwesomeSolid.LINK
		        : FontAwesomeSolid.CODE_BRANCH);
		btnAndOrIcon.setIconSize(DEFAULT_SMALL_ICON_SIZE);

		btnAndOr.getStyleClass().setAll("round-toggle", "andor-toggle"); // <- y aquí
		btnAndOr.setGraphic(btnAndOrIcon);
		btnAndOr.setText(null);
		btnAndOr.setTooltip(new Tooltip(
		    "Alterna entre al menos un tag / todos los tags (Ctrl+Shift+O)"));

		btnAndOr.selectedProperty().addListener((obs, o, sel) -> {
		    btnAndOrIcon.setIconCode(sel ? FontAwesomeSolid.LINK : FontAwesomeSolid.CODE_BRANCH);
		    PREFS.putBoolean(PREF_AND_OR, sel);
		    paginaActual = 1;
		    refrescarTabla();
		    
		    StatusBus.show(
		            sel ? "Filtro de tags: contiene todas" : "Filtro de tags: contiene al menos una",
		            StatusBus.Type.INFO,
		            Duration.seconds(2)
		        );
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
		
	}

	private void setColTitulo() {
		colTitulo.setCellValueFactory(
				cell -> new SimpleStringProperty(cell.getValue().getTitulo()));
		colTitulo.setMaxWidth(200);
		colTitulo.setCellFactory(col -> new TableCell<>() {
		    private final Label lbl = new Label();
		    {
		        lbl.setWrapText(false);               // no partir en varias líneas
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
		colDescripcion.setCellValueFactory(
				cell -> new SimpleStringProperty(cell.getValue().getDescripcion()));
		colDescripcion.setCellFactory(col -> new TableCell<>() {
		    private final Label lbl = new Label();
		    {
		        lbl.setWrapText(false);
		        lbl.setMaxWidth(Double.MAX_VALUE);
		        lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
		    }
		    @Override
		    protected void updateItem(String value, boolean empty) {
		        super.updateItem(value, empty);
		        if (empty || value == null) setGraphic(null);
		        else { lbl.setText(value); lbl.setTooltip(new Tooltip(value)); setGraphic(lbl); }
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
	        private final Button btnEditar = iconButton(
	        		new FontIcon(FontAwesomeRegular.EDIT), "Editar (Ctrl+E)",
	            () -> { Pildora p = (Pildora) getTableRow().getItem(); if (p != null) abrirFormularioEditar(p); }
	        );

	        private final Button btnBorrar = iconButton(
	        		new FontIcon(FontAwesomeSolid.TRASH), "Eliminar (Supr)",
	            () -> {
	                Pildora p = (Pildora) getTableRow().getItem();
	                if (p == null) return;
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
	            }
	        );
	        

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
			BiConsumer<KeyCombination, Runnable> accel = (kc, action) -> scene.getAccelerators()
					.put(kc, action != null ? action : noop);

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
				if (e.getCode() != KeyCode.DELETE) return;

			    // 👇 si venimos de cerrar el detalle tras borrar, ignora 1 DELETE
			    if (ignoreNextDelete) {
			        ignoreNextDelete = false;
			        e.consume();
			        return;
			    }

			    if (root.getCenter() != table) return;
			    Node focus = scene.getFocusOwner();
			    boolean writing = (focus instanceof TextInputControl) || (focus instanceof WebView);
			    if (writing) return;
			    if (!table.isFocused()) return;

			    var sel = table.getSelectionModel().getSelectedItem();
			    if (sel == null) return;

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
			    if (uiOculta) return;

			    Node focus = scene.getFocusOwner();
			    boolean writing = focus instanceof TextInputControl
			                   || focus instanceof WebView; // evita robar PageUp/Down del WebView

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
	            controller.dispose();                 // 👈 limpiar atajos del detalle
	            restaurarFiltrosYPaginacion();
	            refrescarTabla();
	            table.requestFocus();
	        });

	        controller.setOnEdit(pildora -> {
	            controller.dispose();                 // 👈 limpiar antes de ir al editor
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
	                    controller.dispose();         // 👈 limpiar también aquí
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
		            lastState == SortState.ASC
		                ? TableColumn.SortType.ASCENDING
		                : TableColumn.SortType.DESCENDING
		        );
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
	            root.setCenter(centerBackup);  // vuelve el listado
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
	    if (tooltip != null && !tooltip.isBlank()) b.setTooltip(new Tooltip(tooltip));
	    b.setOnAction(e -> { if (action != null) action.run(); });
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
	    icon.setIconSize(11);        // 10–12 suele quedar bien
	    icon.setRotate(45); 

	    var btn = new Button();
	    btn.setGraphic(icon);
	    btn.setText(null);                 // <- sin texto
	    btn.setFocusTraversable(false);
	    btn.setMnemonicParsing(false);
	    btn.getStyleClass().add("clear-field");
	    btn.setOnAction(e -> {
	        if (!tf.getText().isBlank()) {
	            tf.clear();
	            if (onCleared != null) onCleared.run();
	        }
	    });

	    // SLOT fijo (siempre presente)
	    final double SLOT_W = 24;          // 22–26 funciona bien; ajusta si quieres
	    var slot = new StackPane(btn);
	    slot.setMinWidth(SLOT_W);
	    slot.setPrefWidth(SLOT_W);
	    slot.setMaxWidth(SLOT_W);

	    btn.setMinSize(18, 18);
	    btn.setPrefSize(18, 18);
	    btn.setMaxSize(18, 18);
	    StackPane.setMargin(btn, new Insets(0.0,0.0,2.0,0.0));

	    tf.setRight(slot);                 // deja SIEMPRE el slot

	    Runnable update = () -> {
	        boolean show = tf.getText() != null && !tf.getText().isBlank();
	        btn.setVisible(show);
	        btn.setManaged(show);
	    };
	    tf.textProperty().addListener((o, a, b) -> update.run());
	    update.run();
	}

	private void initSettingsMenu() {
	    var gear = new FontIcon(FontAwesomeSolid.COG);
	    btnSettings.setText(null);
	    btnSettings.setGraphic(gear);
	    btnSettings.setTooltip(new Tooltip("Ajustes"));
	    btnSettings.getStyleClass().add("icon-btn");
	    btnSettings.getStyleClass().add("primary");

	    // --- Tipografías disponibles (elige las que prefieras) ---
	    // Puedes añadir más familias; si no están instaladas, JavaFX hará fallback.
	    record Family(String label, String css) {}
	    var families = java.util.List.of(
	        new Family("System",      "System"),
	        new Family("Sans (Arial)","Arial"),
	        new Family("Serif",       "Serif"),
	        new Family("Monospace",   "Consolas")
	    );

	    var miFamily = new javafx.scene.control.Menu("Tipografía");
	    var tgFamily = new javafx.scene.control.ToggleGroup();

	    String currentFamily = PREFS.get(PREF_FONT_FAMILY, DEF_FONT_FAMILY);
	    for (var f : families) {
	        var r = new javafx.scene.control.RadioMenuItem(f.label());
	        r.setToggleGroup(tgFamily);
	        r.setSelected(f.css().equalsIgnoreCase(currentFamily));
	        r.setOnAction(e -> {
	            PREFS.put(PREF_FONT_FAMILY, f.css());
	            applyTypographyNow();
	        });
	        miFamily.getItems().add(r);
	    }

	    // --- Tamaño de letra ---
	    var miSize = new javafx.scene.control.Menu("Tamaño");
	    var tgSize = new javafx.scene.control.ToggleGroup();

	    var sizes = java.util.Map.of(
	        "Pequeño", "small",
	        "Normal",  "normal",
	        "Grande",  "large"
	    );
	    String currentSize = PREFS.get(PREF_FONT_SIZE, DEF_FONT_SIZE);
	    for (var entry : sizes.entrySet()) {
	        var r = new javafx.scene.control.RadioMenuItem(entry.getKey());
	        r.setToggleGroup(tgSize);
	        r.setSelected(entry.getValue().equalsIgnoreCase(currentSize));
	        r.setOnAction(e -> {
	            PREFS.put(PREF_FONT_SIZE, entry.getValue());
	            applyTypographyNow();
	        });
	        miSize.getItems().add(r);
	    }
	    
	    var miTemaOscuro = new CheckMenuItem("Tema oscuro");
	    miTemaOscuro.setSelected(ThemeManager.load() == Theme.DARK);
	    miTemaOscuro.setOnAction(e -> {
	        var scene = root.getScene();
	        if (scene == null) return;

	        boolean wantsDark = miTemaOscuro.isSelected();
	        boolean isDark    = (ThemeManager.load() == Theme.DARK);

	        // Si el estado deseado difiere del actual, alterna
	        if (wantsDark != isDark) {
	            Theme t = ThemeManager.toggle(scene);  // tu helper existente
	            setLogoFor(t);                         // actualiza logo según tema
	            // (opcional) feedback:
	            StatusBus.show(t == Theme.DARK ? "Tema oscuro" : "Tema claro",
	                           StatusBus.Type.INFO, Duration.seconds(2));
	        }
	    });

	    // --- Atajos de teclado ---
	    var miShortcuts = new MenuItem("Atajos de teclado…");
	    miShortcuts.setOnAction(e -> showShortcutsDialog());

	    btnSettings.getItems().setAll(
	        miFamily,
	        miSize,
	        new javafx.scene.control.SeparatorMenuItem(),
	        miTemaOscuro,
	        new SeparatorMenuItem(),
	        miShortcuts
	    );
	}

	/** Aplica familia + tamaño guardados a toda la escena */
	private void applyTypographyNow() {
	    Runnable apply = () -> {
	        var scene = root.getScene();
	        if (scene == null) { Platform.runLater(this::applyTypographyNow); return; }

	        String family = PREFS.get(PREF_FONT_FAMILY, DEF_FONT_FAMILY);
	        String sizeKey = PREFS.get(PREF_FONT_SIZE, DEF_FONT_SIZE);

	        double px = switch (sizeKey) {
	            case "small"  -> 13.0;
	            case "large"  -> 17.0;
	            default       -> 15.0; // normal
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
	      .append("────────\n")
	      .append("Ctrl+F            – Foco en filtro de texto\n")
	      .append("Ctrl+T            – Foco en filtro de tags\n")
	      .append("Ctrl+N            – Nueva píldora\n")
	      .append("Ctrl+O            – Abrir detalle de la fila seleccionada\n")
	      .append("Ctrl+E            – Editar la fila seleccionada\n")
	      .append("Supr              – Eliminar la fila seleccionada\n")
	      .append("M                 – (Tabla enfocada) Alternar favorita\n")
	      .append("Ctrl+Shift+F      – Mostrar solo favoritas (toggle)\n")
	      .append("Ctrl+Shift+O      – Cambiar OR/AND para filtro de tags\n")
	      .append("PageUp / Ctrl+←   – Página anterior\n")
	      .append("PageDown / Ctrl+→ – Página siguiente\n")
	      .append("Esc               – Limpiar filtros (si hay foco en filtros) / Volver\n")
	      .append("Doble clic fila   – Abrir detalle\n\n")
	      .append("DETALLE\n")
	      .append("───────\n")
	      .append("Ctrl+E            – Editar\n")
	      .append("Supr              – Eliminar\n")
	      .append("Esc               – Volver al listado\n")
	      .append("Click en tag      – Filtrar listado por ese tag\n\n")
	      .append("EDITOR\n")
	      .append("──────\n")
	      .append("Ctrl+S            – Guardar\n")
	      .append("Esc               – Cancelar y volver\n")
	      .append("Ctrl+B            – Insertar **negrita**\n")
	      .append("Ctrl+I            – Insertar *cursiva*\n")
	      .append("Ctrl+K            – Insertar [enlace](https://)\n")
	      .append("Ctrl+E            – Insertar `código`\n")
	      .append("Enter / ','       – Confirmar tag en el campo de tags\n")
	      .append("Backspace (tags)  – Con input vacío, borrar el último tag\n");

	    var alert = new Alert(Alert.AlertType.INFORMATION);
	    alert.setTitle("Atajos de teclado");
	    alert.setHeaderText(null);

	    var ta = new javafx.scene.control.TextArea(sb.toString());
	    ta.setEditable(false);
	    ta.setWrapText(false); // sin cortes de línea automáticos
	    ta.setFocusTraversable(false);
	    ta.setPrefColumnCount(48); // ancho aprox
	    ta.setPrefRowCount(24);    // alto aprox
	    ta.setStyle("-fx-font-family: 'Consolas','Monospaced'; -fx-font-size: 13px;");

	    alert.getDialogPane().setContent(ta);
	    alert.getDialogPane().setPrefWidth(560); // opcional, asegura buen ancho

	    Dialogs.decorate(alert, root);
	    alert.showAndWait();
	}



}
