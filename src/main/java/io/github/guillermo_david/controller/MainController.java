package io.github.guillermo_david.controller;


import javafx.fxml.FXML;
import javafx.scene.control.TextArea;


public class MainController {

    @FXML
    private TextArea textArea;

    @FXML
    public void initialize() {
        textArea.setText("Bienvenido, escribe tu primer fragmento aquí...");
    }
}
