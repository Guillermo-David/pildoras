package io.github.guillermo_david.dao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SqlHelper {

    public static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SqlHelper() {}

    public static String esc(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("\u0000", "").replace("'", "''") + "'";
    }

    public static String escBlob(byte[] val) {
        if (val == null) return "NULL";
        StringBuilder sb = new StringBuilder("X'");
        for (byte b : val) sb.append(String.format("%02x", b));
        sb.append('\'');
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isBlank()) return null;
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    public static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.replace('T', ' '), DATETIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
