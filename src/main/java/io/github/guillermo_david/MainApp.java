package io.github.guillermo_david;

import java.util.ArrayList;
import java.util.List;

import io.github.guillermo_david.db.DatabaseHelper;
import io.github.guillermo_david.javafx.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application{

	private static List<Image> loadIcons() {
	    String[] names = {
	        "/icons/gdg_16.png",
	        "/icons/gdg_20.png",
	        "/icons/gdg_24.png",
	        "/icons/gdg_32.png",
	        "/icons/gdg_48.png",
	        "/icons/gdg_64.png",
	        "/icons/gdg_128.png"
	    };
	    ArrayList<Image> list = new ArrayList<>();
	    for (String n : names) {
	        try (var is = MainApp.class.getResourceAsStream(n)) {
	            if (is != null) list.add(new javafx.scene.image.Image(is));
	        } catch (Exception ignore) {}
	    }
	    return list;
	}

	@Override
	public void start(Stage stage) {
	    try {
	        doStart(stage);
	    } catch (Throwable t) {
	        t.printStackTrace();
	        var alert = new javafx.scene.control.Alert(
	            javafx.scene.control.Alert.AlertType.ERROR);
	        alert.setTitle("Error al iniciar");
	        alert.setHeaderText(t.getClass().getName());
	        var sw = new java.io.StringWriter();
	        t.printStackTrace(new java.io.PrintWriter(sw));
	        alert.setContentText(sw.toString());
	        alert.showAndWait();
	        javafx.application.Platform.exit();
	    }
	}

	private void doStart(Stage stage) throws Exception {
	    DatabaseHelper.getInstance();

	    var base = MainApp.class.getResource("/css/base.css").toExternalForm();
	    var themes = java.util.Map.of(
	        "light",           MainApp.class.getResource("/css/theme-light.css").toExternalForm(),
	        "dark",            MainApp.class.getResource("/css/theme-dark.css").toExternalForm(),
	        "nord-light",      MainApp.class.getResource("/css/theme-nord-light.css").toExternalForm(),
	        "nord-dark",       MainApp.class.getResource("/css/theme-nord-dark.css").toExternalForm(),
	        "dracula",         MainApp.class.getResource("/css/theme-dracula.css").toExternalForm(),
	        "solarized-light", MainApp.class.getResource("/css/theme-solarized-light.css").toExternalForm(),
	        "solarized-dark",  MainApp.class.getResource("/css/theme-solarized-dark.css").toExternalForm()
	    );
	    ThemeManager.initStyles(base, themes);

	    FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/fxml/listado-pildoras.fxml"));
	    Scene scene = new Scene(fxmlLoader.load(), 1280, 850);

	    ThemeManager.apply(scene, ThemeManager.loadId());

	    var icons = loadIcons();
	    if (!icons.isEmpty()) stage.getIcons().setAll(icons);

	    stage.setTitle("Pildoras");
	    stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
	    stage.setResizable(true);
	    stage.setMinWidth(900);
	    stage.setMinHeight(600);
	    stage.setScene(scene);

	    io.github.guillermo_david.javafx.ResizeHelper.install(stage);

	    stage.setOnCloseRequest(e -> {
	        DatabaseHelper.getInstance().close();
	    });

	    stage.show();

	    var controller = fxmlLoader.<io.github.guillermo_david.controller.ListadoPildorasController>getController();
	    javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(controller::adjustStageHeight));
	}

	@Override
	public void stop() {
	    DatabaseHelper.getInstance().close();
	}

	public static void main(String[] args) {
		io.github.guillermo_david.util.AppLogger.init();
		launch();
	}
}
