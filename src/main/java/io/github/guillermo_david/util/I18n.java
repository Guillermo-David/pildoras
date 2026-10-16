package io.github.guillermo_david.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public final class I18n {

    private static final String PREF_NODE = "io.github.guillermo_david.pildoras";
    private static final String PREF_KEY  = "locale";
    private static final String BUNDLE    = "messages";

    private static Locale locale;
    private static ResourceBundle bundle;

    private I18n() {}

    static {
        String saved = Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, null);
        if (saved != null && !saved.isBlank()) {
            locale = Locale.forLanguageTag(saved);
        } else {
            Locale sys = Locale.getDefault();
            locale = sys.getLanguage().equals("es") ? new Locale("es") : Locale.ENGLISH;
        }
        bundle = ResourceBundle.getBundle(BUNDLE, locale);
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static Locale getLocale() {
        return locale;
    }

    public static void setLocale(Locale newLocale) {
        locale = newLocale;
        bundle = ResourceBundle.getBundle(BUNDLE, locale);
        Preferences.userRoot().node(PREF_NODE).put(PREF_KEY, locale.toLanguageTag());
    }

    public static boolean isSpanish() {
        return locale.getLanguage().equals("es");
    }
}
