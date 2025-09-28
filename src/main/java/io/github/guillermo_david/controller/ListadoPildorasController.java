package io.github.guillermo_david.controller;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.guillermo_david.MainApp;
import io.github.guillermo_david.dao.PildoraDao;
import io.github.guillermo_david.dao.TagDao;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.model.Pildora;
import io.github.guillermo_david.model.Tag;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ListadoPildorasController {

	private final PildoraDao pildoraDao = new PildoraDao();
	private final TagDao tagDao = new TagDao();

	private int paginaActual = 1;
	private final int TAMANIO_PAGINA = 20;
	private int totalPaginas = 1;

	private boolean uiOculta = false;
	private boolean shortcutsInstalados = false;
	private boolean ignoreNextDelete = false;

	private String columnaOrden = "titulo"; // por defecto
	private String direccionOrden = "ASC";
	
	private double dragOffsetX, dragOffsetY;
	
	private Image logoLight;
	private Image logoDark;
	
	String base = null;
    String light = null;
    String dark  = null;

	private Node topBackup;
	private Node centerBackup;

	@FXML private BorderPane root;
	@FXML private Button btnNueva, btnAnterior, btnSiguiente, btnClose;
	@FXML private HBox paginationBox, statusBar, titleBar;
	@FXML private Label lblPagina, lblStatus;
	@FXML private TableView<Pildora> table;
	@FXML private TableColumn<Pildora, String> colTitulo, colDescripcion, colTags;
	@FXML private TableColumn<Pildora, Void> colFav, colAcciones;
	@FXML private TextField txtFiltroTexto, txtFiltroTags;
	@FXML private ToggleButton btnAndOr, btnSoloFav, btnTema;
	@FXML private ImageView imgLogo;

	@FXML
	public void initialize() {

		base = MainApp.class.getResource("/css/base.css").toExternalForm();
	    light = MainApp.class.getResource("/css/theme-light.css").toExternalForm();
	    dark  = MainApp.class.getResource("/css/theme-dark.css").toExternalForm();
	    
	    logoLight = new Image(getClass().getResource("/icons/gdg_B.png").toExternalForm(), 0, 64, true, true);
	    logoDark  = new Image(getClass().getResource("/icons/gdg_W.png").toExternalForm(),  0, 64, true, true);

		setStatusBar();
		setTableProperties();
		setColFav();
		setColTitulo();
		setColDescripcion();
		setColTags();
		setColAcciones();
		setBotones();
		cargarTabla(null, null);
		setLogo();
		setTxtFiltros();
		setSceneProperties();
		initThemeToggle();
		hookLogoToTheme();
		initCustomTitleBar();
	}

	private void setTxtFiltros() {
		txtFiltroTexto.setOnAction(e -> { paginaActual = 1; refrescarTabla(); });
		txtFiltroTags.setOnAction(e -> { paginaActual = 1; refrescarTabla(); });
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

		        // 👇 Limpia el binding previo antes de re-vincular
		        table.prefHeightProperty().unbind();
		        table.prefHeightProperty().bind(headerHeight.add(rows * row + 2));

		        table.setMinHeight(Region.USE_COMPUTED_SIZE);
		        table.setMaxHeight(Region.USE_COMPUTED_SIZE);

		        var wnd = table.getScene() != null ? table.getScene().getWindow() : null;
		        if (wnd instanceof Stage st) Platform.runLater(st::sizeToScene);
		    });
		});
		table.getSortOrder().addListener((ListChangeListener<TableColumn<Pildora, ?>>) change -> {
			if (!table.getSortOrder().isEmpty()) {
				TableColumn<Pildora, ?> col = table.getSortOrder().get(0);
				columnaOrden = switch (col.getText()) {
				case "Título" -> "titulo";
				case "Descripción" -> "descripcion";
				case "Tags" -> "nombre"; // ojo, esto requiere join, podemos dejarlo vacío de momento
				default -> "fecha_creacion";
				};
				direccionOrden = col.getSortType() == TableColumn.SortType.ASCENDING ? "ASC" : "DESC";
				
				paginaActual = 1; // reset al ordenar
				refrescarTabla();
			}
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
				if (p.isFavorita())
					icon.getStyleClass().add("star-fav"); // 👈 color dorado

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
				setGraphic(box);
			}
		});
	}

	private void setBotones() {
		// Icono del toggle de favoritas (igual que en la tabla)
		FontIcon favTopIcon = new FontIcon(btnSoloFav.isSelected() ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
		favTopIcon.getStyleClass().add("star-icon"); // clase propia para CSS
		
		btnSoloFav.setText(null);                    // sin texto, solo icono
		btnSoloFav.setGraphic(favTopIcon);
		btnSoloFav.setTooltip(new Tooltip("Solo favoritas (Ctrl+Shift+F)"));
		
		// Cambia icono y color al (de)seleccionar
		btnSoloFav.selectedProperty().addListener((obs, old, sel) -> {
			favTopIcon.setIconCode(sel ? FontAwesomeSolid.STAR : FontAwesomeRegular.STAR);
			favTopIcon.getStyleClass().remove("star-fav");
			if (sel) favTopIcon.getStyleClass().add("star-fav");
			paginaActual = 1;
			refrescarTabla();
		});
		
		btnAndOr.setTooltip(new Tooltip(
				"• OR: muestra las píldoras que tengan al menos un tag"
						+ "\n"
						+ "• AND: muestra solo las que tengan todos los tags"));
		btnAndOr.setOnAction(e -> {
			btnAndOr.setText(btnAndOr.isSelected() ? "AND" : "OR");
			paginaActual = 1;
			refrescarTabla();
		});
		
		btnNueva.setText("");
		btnNueva.setTooltip(new Tooltip("Nueva (Ctrl+N)"));
		btnNueva.setOnAction(e -> abrirFormularioNueva());
//		btnNueva.getStyleClass().add("button-right");
		btnNueva.getStyleClass().add("icon-btn");

		FontIcon nuevaIcon = new FontIcon(FontAwesomeSolid.PLUS);
		btnNueva.setGraphic(nuevaIcon);
		
		
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
		
		// Botón de tema con dos iconos superpuestos y tamaño fijo
		btnTema.getStyleClass().add("theme-toggle");
		btnTema.setText(null);

		var moon = new FontIcon(FontAwesomeRegular.MOON);
		var sun  = new FontIcon(FontAwesomeRegular.SUN);
		moon.setIconSize(18);
		sun.setIconSize(18);

		// StackPane con ambos iconos, mostramos uno u otro según el estado
		var iconSwap = new javafx.scene.layout.StackPane(moon, sun);
		sun.visibleProperty().bind(btnTema.selectedProperty());           // seleccionado = sol
		moon.visibleProperty().bind(btnTema.selectedProperty().not());    // no seleccionado = luna
		btnTema.setGraphic(iconSwap);

		// si cambias el tema, aquí disparas tu lógica de tema:
		btnTema.selectedProperty().addListener((o, old, sel) -> {
		    setTheme(sel); // sel=true -> dark, o al revés según tu implementación
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
	    colAcciones.setMaxWidth(120);
	    colAcciones.setSortable(false);

	    colAcciones.setCellFactory(col -> new TableCell<>() {
	        private final Button btnEditar = iconButton(
	        		new FontIcon(FontAwesomeRegular.EDIT), "Editar (Ctrl+E)",
	            () -> { Pildora p = (Pildora) getTableRow().getItem(); if (p != null) abrirFormularioEditar(p); }
	        );

	        private final Button btnBorrar = iconButton(
	        		new FontIcon(FontAwesomeRegular.TRASH_ALT), "Eliminar (Supr)",
	            () -> {
	                Pildora p = (Pildora) getTableRow().getItem();
	                if (p == null) return;
	                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
	                        "¿Seguro que quieres eliminar la píldora \"" + p.getTitulo() + "\"?");
	                decorate(confirm);
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
	        }

	        @Override
	        protected void updateItem(Void item, boolean empty) {
	            super.updateItem(item, empty);
	            setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : box);
	            btnBorrar.getStyleClass().add("delete");
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
						btnAndOr.setText("OR");
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
			topBackup = root.getTop();
			centerBackup = root.getCenter();
			root.setTop(null);
			// 👇 oculta solo la paginación, NO la barra de estado
			paginationBox.setManaged(false);
			paginationBox.setVisible(false);
			uiOculta = true;
		}
	}

	private void restaurarFiltrosYPaginacion() {
		if (uiOculta) {
			root.setTop(topBackup);
//            root.setBottom(bottomBackup);
			root.setCenter(centerBackup);
			paginationBox.setManaged(true);
			paginationBox.setVisible(true);
			uiOculta = false;
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
	
	private void setLogo() {
	    var url = getClass().getResource("/icons/gdg_B.png");
	    if (url != null) {
	        imgLogo.setImage(new Image(url.toExternalForm(), 0, 64, true, true));
	        imgLogo.setSmooth(true);
	    }
	}
	
	private void decorate(Alert alert) {
	    // owner = stage principal
	    var owner = (javafx.stage.Stage) root.getScene().getWindow();
	    alert.initOwner(owner);

	    // setear los mismos iconos al Stage del diálogo
	    var dialogStage = (javafx.stage.Stage) alert.getDialogPane().getScene().getWindow();
	    dialogStage.getIcons().setAll(owner.getIcons());
	}
	
	private void hookLogoToTheme() {
	    // Cuando haya Scene, coloca el logo que toque según el tema actual
	    root.sceneProperty().addListener((obs, old, scene) -> {
	        if (scene == null) return;
	        boolean isDark = scene.getStylesheets().contains(dark);
	        imgLogo.setImage(isDark ? logoDark : logoLight);
	    });
	}

	// Llama a este método cuando cambies de tema (donde haces el toggle)
	private void applyTheme(boolean darkMode) {
	    var scene = root.getScene();
	    if (scene == null) return;
	    var ss = scene.getStylesheets();
	    ss.clear();
	    ss.add(base);
	    ss.add(darkMode ? dark : light);

	    // Actualiza logo
	    imgLogo.setImage(darkMode ? logoDark : logoLight);
	}
	
	private void initThemeToggle() {
	    root.sceneProperty().addListener((obs, oldScene, scene) -> {
	        if (scene == null) return;

	        boolean isDark = scene.getStylesheets().contains(dark);
	        btnTema.setSelected(isDark);
	        updateThemeIcon(isDark);

	        btnTema.selectedProperty().addListener((o, oldVal, darkMode) -> {
	            applyTheme(darkMode);
	            updateThemeIcon(darkMode);
	        });
	    });
	}

	private void updateThemeIcon(boolean darkMode) {
	    // con Ikonli:
	    var icon = new FontIcon(darkMode ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON);
	    icon.getStyleClass().add("star-icon"); // hereda color del tema
	    btnTema.setGraphic(icon);
	    btnTema.setText(null); // solo icono
	}

	void setTheme(boolean darkMode) {
		var scene = root.getScene();
	    var ss = scene.getStylesheets();
	    ss.clear();
	    ss.add(base);
	    ss.add(darkMode ? dark : light);
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
}
