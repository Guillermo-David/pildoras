package io.github.guillermo_david.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cifra texto con DPAPI (ligado al usuario de Windows). En otros sistemas, o si falla,
 * se guarda en claro. Los valores protegidos llevan el prefijo "dpapi:".
 */
final class DeviceProtector {

    static final String PREFIX = "dpapi:";

    private DeviceProtector() {}

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static String protect(String plain) {
        if (plain == null || !windows())
            return plain;
        try {
            byte[] enc = com.sun.jna.platform.win32.Crypt32Util
                    .cryptProtectData(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(enc);
        } catch (Throwable t) {
            return plain;
        }
    }

    /** Devuelve null si el valor estaba protegido y no se puede descifrar aquí. */
    static String unprotect(String stored) {
        if (stored == null || !stored.startsWith(PREFIX))
            return stored;
        if (!windows())
            return null;
        try {
            byte[] dec = com.sun.jna.platform.win32.Crypt32Util
                    .cryptUnprotectData(Base64.getDecoder().decode(stored.substring(PREFIX.length())));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
}
