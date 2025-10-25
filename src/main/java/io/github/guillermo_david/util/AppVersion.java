package io.github.guillermo_david.util;

import io.github.guillermo_david.MainApp;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.Manifest;

public final class AppVersion {
    private AppVersion() {}

    public static String get() {
        // 1) Permite forzarla en arranque: -Dapp.version=1.2.3
        String v = System.getProperty("app.version");
        if (v != null && !v.isBlank()) return v;

        // 2) MANIFEST: Implementation-Version (shade/jar suele ponerla)
        Package p = MainApp.class.getPackage();
        if (p != null) {
            String impl = p.getImplementationVersion();
            if (impl != null && !impl.isBlank()) return impl;
        }
        try (InputStream is = MainApp.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                Manifest m = new Manifest(is);
                String mv = m.getMainAttributes().getValue("Implementation-Version");
                if (mv != null && !mv.isBlank()) return mv;
            }
        } catch (Exception ignore) {}

        // 3) (opcional) version.txt si lo añades al resources
        try (InputStream is = MainApp.class.getResourceAsStream("/version.txt")) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignore) {}

        // 4) Dev por defecto
        return "dev";
    }
}
