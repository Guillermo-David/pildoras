package io.github.guillermo_david;

import java.util.ArrayList;
import java.util.List;

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
	    FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/fxml/listado-pildoras.fxml"));
	    Scene scene = new Scene(fxmlLoader.load(), 1024, 794);

	    var css = MainApp.class.getResource("/css/app.css");
	    if (css != null) scene.getStylesheets().add(css.toExternalForm());

	    // 👇 Establece iconos PNG (con alpha) ANTES del show()
	    var icons = loadIcons();
	    if (!icons.isEmpty()) stage.getIcons().setAll(icons);

	    stage.setTitle("Píldoras");
	    stage.initStyle(javafx.stage.StageStyle.DECORATED); // asegúrate de no usar UNIFIED/TRANSPARENT
	    stage.setResizable(false);
	    stage.setScene(scene);
	    stage.show();
	}


	public static void main(String[] args) {
		System.setProperty("prism.order", "sw");
		launch();
	}
}
