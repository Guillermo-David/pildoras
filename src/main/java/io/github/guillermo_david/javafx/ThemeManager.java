package io.github.guillermo_david.javafx;

import java.util.prefs.Preferences;

import javafx.scene.Scene;

public final class ThemeManager {
    public enum Theme { LIGHT, DARK }

    private static final String PREF_NODE = "io.github.guillermo_david.pildoras";
    private static final String PREF_KEY  = "theme";
    private static String BASE, LIGHT, DARK;

    private ThemeManager() {}

    /** Registra las hojas de estilo (llamar una vez al arrancar). */
    public static void initStyles(String base, String light, String dark) {
        BASE = base; LIGHT = light; DARK = dark;
    }

    /** Lee el tema guardado (por defecto LIGHT). */
    public static Theme load() {
        var p = Preferences.userRoot().node(PREF_NODE);
        String v = p.get(PREF_KEY, "LIGHT");
        return "DARK".equalsIgnoreCase(v) ? Theme.DARK : Theme.LIGHT;
    }

    /** Guarda el tema. */
    public static void save(Theme t) {
        var p = Preferences.userRoot().node(PREF_NODE);
        p.put(PREF_KEY, t.name());
    }

    /** Aplica el tema a la escena. */
    public static void apply(Scene scene, Theme t) {
        if (BASE == null || LIGHT == null || DARK == null) return; // por si acaso
        var ss = scene.getStylesheets();
        ss.setAll(BASE, t == Theme.DARK ? DARK : LIGHT);

        // Pseudo-clase para CSS condicional, útil si quieres selectores .dark:
        var root = scene.getRoot();
        root.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("dark"), t == Theme.DARK);
    }

    /** Alterna y guarda. Devuelve el nuevo tema. */
    public static Theme toggle(Scene scene) {
        Theme next = (load() == Theme.DARK) ? Theme.LIGHT : Theme.DARK;
        apply(scene, next);
        save(next);
        return next;
    }
}
