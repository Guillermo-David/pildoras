package io.github.guillermo_david.sync;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

final class DriveBackend {
    static Optional<Path> findMostRecentLatest(Path driveRoot) throws IOException {
        try (Stream<Path> s = Files.list(driveRoot)) {
            return s.filter(p -> p.getFileName().toString().startsWith("pildoras-latest-") && p.getFileName().toString().endsWith(".zip"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        }
    }

    static String histName(Manifest mf, boolean conflict) {
        var ts = Instant.parse(mf.timestampUtc).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        var shortHash = mf.sha256Zip != null && mf.sha256Zip.length() >= 8 ? mf.sha256Zip.substring(0, 8) : "nohash";
        return "pildoras-" + ts + "-" + mf.deviceId + "-" + shortHash + (conflict ? "_CONFLICT" : "") + ".zip";
    }
}

final class RetentionPolicy {
    static void rotate(Path histDir, String deviceId) {
        try (var s = Files.list(histDir)
                .filter(p -> p.getFileName().toString().matches("pildoras-\\d{8}-\\d{6}-" + deviceId + "-[a-fA-F0-9]{8}(_CONFLICT)?\\.zip"))
                .sorted(Comparator.comparingLong(p -> ((Path) p).toFile().lastModified()).reversed())) {

            var list = s.toList();
            // Mantener 10 últimos por dispositivo
            for (int i = 10; i < list.size(); i++) {
                try { Files.deleteIfExists(list.get(i)); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
    }
}

final class Zipper {
    static void zipDirectory(Path dir, Path zip) throws IOException {
        try (var zs = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            Files.walk(dir).forEach(path -> {
                try {
                    var rel = dir.relativize(path).toString().replace("\\", "/");
                    if (rel.isEmpty()) return;
                    var entry = new java.util.zip.ZipEntry(rel + (Files.isDirectory(path) ? "/" : ""));
                    if (Files.isDirectory(path)) {
                        zs.putNextEntry(entry); zs.closeEntry();
                    } else {
                        zs.putNextEntry(entry);
                        Files.copy(path, zs);
                        zs.closeEntry();
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }
}

final class Integrity {
	static String sha256Concat(Path... files) throws IOException {
	    try {
	        var md = java.security.MessageDigest.getInstance("SHA-256");
	        byte[] buf = new byte[8192];
	        for (Path p : files) {
	            try (var in = java.nio.file.Files.newInputStream(p)) {
	                int r;
	                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
	            }
	            // separador para evitar colisiones por concatenación (opcional):
	            md.update((byte) 0); md.update((byte) 255);
	        }
	        byte[] bytes = md.digest();
	        var sb = new StringBuilder(bytes.length * 2);
	        for (byte b : bytes) sb.append(String.format("%02x", b));
	        return sb.toString();
	    } catch (java.security.NoSuchAlgorithmException e) {
	        throw new IllegalStateException(e);
	    }
	}
}


final class FileUtils {
    static void moveAtomic(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    static void deleteRecursive(Path p) {
        if (p == null) return;
        try {
            if (!Files.exists(p)) return;
            Files.walk(p)
                 .sorted(Comparator.reverseOrder())
                 .forEach(pp -> { try { Files.deleteIfExists(pp); } catch (Exception ignore) {} });
        } catch (Exception ignore) {}
    }
}
