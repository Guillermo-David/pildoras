package io.github.guillermo_david.db;

import java.util.prefs.Preferences;

public final class TursoConfig {

    private static final Preferences PREFS =
            Preferences.userRoot().node("io.github.guillermo_david.pildoras.turso");

    private static final String KEY_URL   = "tursoUrl";
    private static final String KEY_TOKEN = "tursoToken";

    private TursoConfig() {}

    public static String getUrl() {
        return PREFS.get(KEY_URL, "");
    }

    public static String getToken() {
        return PREFS.get(KEY_TOKEN, "");
    }

    public static boolean isConfigured() {
        return !getUrl().isBlank() && !getToken().isBlank();
    }

    public static void save(String url, String token) {
        PREFS.put(KEY_URL, url == null ? "" : url.trim());
        PREFS.put(KEY_TOKEN, token == null ? "" : token.trim());
    }

    public static void clear() {
        PREFS.remove(KEY_URL);
        PREFS.remove(KEY_TOKEN);
    }

    public static String toJdbcUrl(String tursoUrl) {
        String u = tursoUrl.trim();
        if (u.startsWith("libsql://")) {
            u = "https://" + u.substring("libsql://".length());
        }
        if (!u.startsWith("jdbc:dbeaver:libsql:")) {
            u = "jdbc:dbeaver:libsql:" + u;
        }
        return u;
    }
}
