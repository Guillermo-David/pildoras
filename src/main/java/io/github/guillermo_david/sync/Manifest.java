package io.github.guillermo_david.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Manifest {
    public int versionSchema;
    public String timestampUtc; // ISO-8601
    public String deviceId;
    public Long sizeBytes;
    public String sha256Zip;
    public String dataSha256;

    // (Opcional) métricas:
    public Integer totalPildoras;
    public String ultimoFechaActualizacion; // ISO-8601
    public String appVersion;

    private static final Gson GSON = new GsonBuilder().create();

    public static Manifest readFromZip(Path zip) {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("manifest.json");
            if (e == null) return null;
            try (var r = new InputStreamReader(zf.getInputStream(e))) {
                return GSON.fromJson(r, Manifest.class);
            }
        } catch (IOException ex) {
            return null;
        }
    }
}
