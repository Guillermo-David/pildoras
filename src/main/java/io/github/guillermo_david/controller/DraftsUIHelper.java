package io.github.guillermo_david.controller;

import java.util.function.Supplier;

import io.github.guillermo_david.dao.DraftDao;
import io.github.guillermo_david.javafx.DraftDialogs;
import io.github.guillermo_david.javafx.PinDialogs;
import io.github.guillermo_david.javafx.StatusBus;
import io.github.guillermo_david.model.Draft;
import io.github.guillermo_david.security.SecurityService;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

class DraftsUIHelper {

    private final BorderPane root;
    private final SecurityService security;
    private final MenuButton btnBorradores;
    private final Runnable refrescarTabla;
    private final Supplier<Node> centerBackup;

    private Label draftsBadge;
    private StackPane draftsIconStack;

    DraftsUIHelper(BorderPane root, SecurityService security, MenuButton btnBorradores,
                   Runnable refrescarTabla, Supplier<Node> centerBackup) {
        this.root = root;
        this.security = security;
        this.btnBorradores = btnBorradores;
        this.refrescarTabla = refrescarTabla;
        this.centerBackup = centerBackup;
    }

    void init() {
        if (btnBorradores == null) return;

        var eraser = new FontIcon(FontAwesomeSolid.ERASER);
        eraser.setIconSize(16);

        draftsBadge = new Label();
        draftsBadge.getStyleClass().add("badge");
        draftsBadge.setVisible(false);
        draftsBadge.setManaged(false);

        draftsIconStack = new StackPane(eraser, draftsBadge);
        draftsIconStack.setMinSize(24, 24);
        draftsIconStack.setPrefSize(24, 24);
        draftsIconStack.setMaxSize(24, 24);

        StackPane.setAlignment(eraser, Pos.CENTER);
        StackPane.setAlignment(draftsBadge, Pos.TOP_RIGHT);
        draftsBadge.setTranslateX(8);
        draftsBadge.setTranslateY(-8);
        draftsBadge.setMaxSize(16, 16);

        btnBorradores.setText(null);
        btnBorradores.setGraphic(draftsIconStack);
        btnBorradores.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btnBorradores.setTooltip(new Tooltip("Borradores"));
        btnBorradores.getStyleClass().addAll("icon-btn");

        var miNuevo = new MenuItem("Nuevo borrador…");
        miNuevo.setOnAction(e -> {
            var res = DraftDialogs.showQuickDraftDialog(root, null, null, false);
            if (res == null) return;

            switch (res.action) {
                case SAVE -> {
                    new DraftDao().insertar(res.draft);
                    StatusBus.show("Borrador guardado.", StatusBus.Type.SUCCESS, Duration.seconds(2));
                    refreshBadge();
                }
                case CONVERT -> {
                    openEditorPrefilled(res.draft.getTitulo(),
                            res.draft.isProtegida() ? tryDecryptToString(res.draft) : res.draft.getContenido(),
                            res.draft.isProtegida());
                }
                default -> {}
            }
        });

        var miBandeja = new MenuItem("Bandeja de borradores…");
        miBandeja.setOnAction(e -> {
            DraftDialogs.showDraftsTray(root,
                    dft -> {
                        var plain = dft.isProtegida() ? tryDecryptToString(dft) : dft.getContenido();
                        var res = DraftDialogs.showQuickDraftDialog(root,
                                dft.getTitulo(), plain, dft.isProtegida());
                        if (res == null) return;

                        switch (res.action) {
                            case SAVE -> {
                                dft.setTitulo(res.draft.getTitulo());
                                dft.setProtegida(res.draft.isProtegida());
                                dft.setContenido(res.draft.getContenido());
                                dft.setContenidoCipher(res.draft.getContenidoCipher());
                                dft.setContenidoIv(res.draft.getContenidoIv());
                                new DraftDao().actualizar(dft);
                                StatusBus.show("Borrador actualizado.", StatusBus.Type.SUCCESS,
                                        Duration.seconds(2));
                            }
                            case CONVERT -> {
                                openEditorPrefilled(res.draft.getTitulo(),
                                        res.draft.isProtegida() ? tryDecryptToString(res.draft)
                                                : res.draft.getContenido(),
                                        res.draft.isProtegida());
                                new DraftDao().eliminar(dft.getId());
                            }
                            default -> {}
                        }
                        refreshBadge();
                    },
                    dft -> {
                        openEditorPrefilled(dft.getTitulo(),
                                dft.isProtegida() ? tryDecryptToString(dft) : dft.getContenido(),
                                dft.isProtegida());
                        refreshBadge();
                    },
                    dft -> StatusBus.show("Borrador eliminado.", StatusBus.Type.INFO, Duration.seconds(2)));
            refreshBadge();
        });

        btnBorradores.getItems().setAll(miNuevo, miBandeja);
        refreshBadge();
    }

    void refreshBadge() {
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

    String tryDecryptToString(Draft dft) {
        try {
            if (dft == null || !dft.isProtegida())
                return dft == null ? "" : (dft.getContenido() == null ? "" : dft.getContenido());
            if (!security.isUnlocked()) {
                if (!security.ensureUnlocked(
                        () -> PinDialogs.promptPin6(root, true, java.time.Duration.ofMinutes(10)))) {
                    return "";
                }
            }
            return security.decrypt(dft.getContenidoCipher(), dft.getContenidoIv());
        } catch (Exception ex) {
            return "";
        }
    }

    private void openEditorPrefilled(String titulo, String cuerpo, boolean proteger) {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/editor-pildora.fxml"));
            Node rootEditor = loader.load();
            EditorPildoraController ctrl = loader.getController();

            ctrl.setPrefill(titulo, cuerpo, proteger);

            ctrl.setOnClose(() -> {
                root.setCenter(centerBackup.get());
                refrescarTabla.run();
            });

            root.setCenter(rootEditor);
        } catch (Exception ex) {
            ex.printStackTrace();
            StatusBus.show("No se pudo abrir el editor.", StatusBus.Type.ERROR, Duration.seconds(3));
        }
    }
}
