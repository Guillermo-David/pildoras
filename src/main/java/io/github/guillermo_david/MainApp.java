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
	public void start(Stage stage) throws Exception {
		
		// 1) Fuerza inicialización + migraciones de la BD primero
	    DatabaseHelper.getInstance();  // 👈 esto dispara initializeDatabase()

	    // 2) Ya puedes cargar los FXML/Controllers con la BD migrada
	    FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/fxml/listado-pildoras.fxml"));
	    Scene scene = new Scene(fxmlLoader.load(), 1280, 837);

	    var base = MainApp.class.getResource("/css/base.css").toExternalForm();
	    var light = MainApp.class.getResource("/css/theme-light.css").toExternalForm();
	    var dark  = MainApp.class.getResource("/css/theme-dark.css").toExternalForm();

	    ThemeManager.initStyles(base, light, dark);
	    var current = ThemeManager.load();
	    ThemeManager.apply(scene, current);
	    
	    var icons = loadIcons();
	    if (!icons.isEmpty()) stage.getIcons().setAll(icons);

	    stage.setTitle("Píldoras");
	    stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
	    stage.setResizable(false);
	    stage.setScene(scene);
	    stage.show();
	}

	public static void main(String[] args) {
		System.setProperty("prism.order", "sw");
		launch();
	}
}
