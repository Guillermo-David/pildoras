package io.github.guillermo_david.javafx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import io.github.guillermo_david.util.I18n;
import javafx.scene.Scene;

public final class ThemeManager {
    public enum Theme { LIGHT, DARK }

    public record ThemeEntry(String id, String label, Theme base, String cssPath) {}

    private static final String PREF_NODE = "io.github.guillermo_david.pildoras";
    private static final String PREF_KEY  = "theme";
    private static String BASE;
    private static final Map<String, ThemeEntry> THEMES = new LinkedHashMap<>();

    private ThemeManager() {}

    public static void initStyles(String baseCss, Map<String, String> themeCssPaths) {
        BASE = baseCss;
        THEMES.clear();
        THEMES.put("light",          new ThemeEntry("light",          I18n.get("theme.light"),            Theme.LIGHT, themeCssPaths.get("light")));
        THEMES.put("dark",           new ThemeEntry("dark",           I18n.get("theme.dark"),             Theme.DARK,  themeCssPaths.get("dark")));
        THEMES.put("nord-light",     new ThemeEntry("nord-light",     I18n.get("theme.nord.light"),       Theme.LIGHT, themeCssPaths.get("nord-light")));
        THEMES.put("nord-dark",      new ThemeEntry("nord-dark",      I18n.get("theme.nord.dark"),        Theme.DARK,  themeCssPaths.get("nord-dark")));
        THEMES.put("dracula",        new ThemeEntry("dracula",        I18n.get("theme.dracula"),          Theme.DARK,  themeCssPaths.get("dracula")));
        THEMES.put("solarized-light", new ThemeEntry("solarized-light", I18n.get("theme.solarized.light"), Theme.LIGHT, themeCssPaths.get("solarized-light")));
        THEMES.put("solarized-dark", new ThemeEntry("solarized-dark", I18n.get("theme.solarized.dark"),   Theme.DARK,  themeCssPaths.get("solarized-dark")));
    }

    public static Map<String, ThemeEntry> getThemes() {
        return THEMES;
    }

    public static String loadId() {
        var p = Preferences.userRoot().node(PREF_NODE);
        String v = p.get(PREF_KEY, "light");
        if ("DARK".equalsIgnoreCase(v)) return "dark";
        if ("LIGHT".equalsIgnoreCase(v)) return "light";
        return THEMES.containsKey(v) ? v : "light";
    }

    public static Theme load() {
        var entry = THEMES.get(loadId());
        return entry != null ? entry.base() : Theme.LIGHT;
    }

    public static void save(String themeId) {
        var p = Preferences.userRoot().node(PREF_NODE);
        p.put(PREF_KEY, themeId);
    }

    public static void apply(Scene scene, String themeId) {
        if (BASE == null) return;
        var entry = THEMES.get(themeId);
        if (entry == null) entry = THEMES.get("light");
        var ss = scene.getStylesheets();
        ss.setAll(BASE, entry.cssPath());
        var root = scene.getRoot();
        root.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("dark"), entry.base() == Theme.DARK);
    }

    public static void apply(Scene scene, Theme t) {
        apply(scene, t == Theme.DARK ? "dark" : "light");
    }

    public static Theme toggle(Scene scene) {
        String current = loadId();
        String next = (load() == Theme.DARK) ? "light" : "dark";
        apply(scene, next);
        save(next);
        return THEMES.get(next).base();
    }

    /** @deprecated Use initStyles(String, Map) instead */
    @Deprecated
    public static void initStyles(String base, String light, String dark) {
        BASE = base;
        THEMES.clear();
        THEMES.put("light", new ThemeEntry("light", I18n.get("theme.light"), Theme.LIGHT, light));
        THEMES.put("dark",  new ThemeEntry("dark",  I18n.get("theme.dark"), Theme.DARK,  dark));
    }
}
