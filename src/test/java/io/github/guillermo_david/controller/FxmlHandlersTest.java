package io.github.guillermo_david.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class FxmlHandlersTest {

    private static final Map<String, Class<?>> VIEWS = Map.of(
            "/fxml/editor-pildora.fxml", EditorPildoraController.class,
            "/fxml/detalle-pildora.fxml", DetallePildoraController.class,
            "/fxml/listado-pildoras.fxml", ListadoPildorasController.class);

    @Test
    void everyFxmlHandlerExistsInItsController() throws Exception {
        var handler = Pattern.compile("on\\w+=\"#(\\w+)\"");
        for (var e : VIEWS.entrySet()) {
            String xml;
            try (InputStream in = getClass().getResourceAsStream(e.getKey())) {
                xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            var m = handler.matcher(xml);
            while (m.find()) {
                String name = m.group(1);
                boolean found = false;
                for (Method method : e.getValue().getDeclaredMethods())
                    found |= method.getName().equals(name)
                            && method.isAnnotationPresent(javafx.fxml.FXML.class)
                            || method.getName().equals(name) && java.lang.reflect.Modifier.isPublic(method.getModifiers());
                assertTrue(found, e.getKey() + " -> #" + name);
            }
        }
    }
}
