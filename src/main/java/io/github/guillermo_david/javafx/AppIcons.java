// io/github/guillermo_david/javafx/AppIcons.java
package io.github.guillermo_david.javafx;

import java.util.List;

import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AppIcons {
    private static final List<Image> ICONS = List.of(
        img("/icons/gdg.ico", 16),
        img("/icons/gdg.ico", 20),
        img("/icons/gdg.ico", 24),
        img("/icons/gdg.ico", 32),
        img("/icons/gdg.ico", 48),
        img("/icons/gdg.ico", 64),
        img("/icons/gdg.ico", 128),
        img("/icons/gdg.ico", 256)
    );

    private static Image img(String path, int size) {
        var url = AppIcons.class.getResource(path);
        return new Image(url.toExternalForm(), size, size, true, true);
    }

    /** Aplica iconos a cualquier ventana. */
    public static void applyTo(Window window) {
        if (window instanceof Stage s) {
            s.getIcons().setAll(ICONS);
        }
    }

    /** Comodidad: aplica iconos a un Alert ya creado. */
    public static void applyTo(Alert alert) {
        var scene = alert.getDialogPane().getScene();
        if (scene != null) applyTo(scene.getWindow());
        else {
            // Si aún no hay Scene, difiere a cuando se cree
            alert.dialogPaneProperty().addListener((o, old, dp) -> {
                if (dp != null && dp.getScene() != null) {
                    applyTo(dp.getScene().getWindow());
                }
            });
        }
    }
}

