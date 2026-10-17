package io.github.guillermo_david.export;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

class BackupCryptoTest {

    @Test
    void roundTrip() throws Exception {
        String env = BackupService.encrypt("{\"a\":1}", "secreto".toCharArray());
        assertFalse(env.contains("\"a\""));
        assertEquals("{\"a\":1}", BackupService.decrypt(env, "secreto".toCharArray()));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String env = BackupService.encrypt("{}", "uno".toCharArray());
        assertThrows(BackupService.WrongPasswordException.class, () -> BackupService.decrypt(env, "dos".toCharArray()));
    }

    @Test
    void detectsEncryptedFiles() throws Exception {
        var enc = Files.createTempFile("b", ".json");
        Files.writeString(enc, BackupService.encrypt("{}", "x".toCharArray()));
        var plain = Files.createTempFile("b", ".json");
        Files.writeString(plain, "{\"version\":\"1.0\"}");
        assertTrue(BackupService.isEncrypted(enc.toFile()));
        assertFalse(BackupService.isEncrypted(plain.toFile()));
    }
}
