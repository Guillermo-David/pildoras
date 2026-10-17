package io.github.guillermo_david.security;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityServiceTest {

    private final SecurityService sec = SecurityService.getInstance();

    @BeforeEach
    void resetState() throws Exception {
        Preferences prefs = Preferences.userNodeForPackage(SecurityService.class);
        prefs.clear();
        prefs.flush();

        Field cmkField = SecurityService.class.getDeclaredField("cmk");
        cmkField.setAccessible(true);
        cmkField.set(sec, null);

        Field validField = SecurityService.class.getDeclaredField("cmkValidUntil");
        validField.setAccessible(true);
        validField.set(sec, Long.MAX_VALUE);
    }

    private static char[] pin(String s) {
        return s.toCharArray();
    }

    @Test
    void initialStateHasNoPin() {
        assertFalse(sec.hasPin());
        assertFalse(sec.isUnlocked());
    }

    @Test
    void setupPinAndVerify() {
        sec.setupPin(pin("123456"));
        assertTrue(sec.hasPin());
        assertTrue(sec.isUnlocked());

        sec.lockNow();
        assertFalse(sec.isUnlocked());

        assertTrue(sec.verifyPin(pin("123456")));
        assertTrue(sec.isUnlocked());
    }

    @Test
    void wrongPinFails() {
        sec.setupPin(pin("123456"));
        sec.lockNow();

        assertFalse(sec.verifyPin(pin("654321")));
        assertFalse(sec.isUnlocked());
    }

    @Test
    void pinMustBeSixDigits() {
        assertThrows(IllegalArgumentException.class, () -> sec.setupPin(pin("1234")));
        assertThrows(IllegalArgumentException.class, () -> sec.setupPin(pin("abcdef")));
        assertThrows(IllegalArgumentException.class, () -> sec.setupPin(pin("1234567")));
    }

    @Test
    void encryptDecryptRoundTrip() {
        sec.setupPin(pin("123456"));

        String plaintext = "Contenido secreto de la píldora";
        SecurityService.Encrypted enc = sec.encrypt(plaintext);

        assertNotNull(enc.cipher);
        assertNotNull(enc.iv);
        assertTrue(enc.cipher.length > 0);

        String decrypted = sec.decrypt(enc.cipher, enc.iv);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptEmptyString() {
        sec.setupPin(pin("123456"));

        SecurityService.Encrypted enc = sec.encrypt("");
        assertEquals("", sec.decrypt(enc.cipher, enc.iv));
    }

    @Test
    void encryptNullTreatedAsEmpty() {
        sec.setupPin(pin("123456"));

        SecurityService.Encrypted enc = sec.encrypt(null);
        assertEquals("", sec.decrypt(enc.cipher, enc.iv));
    }

    @Test
    void encryptRequiresCmk() {
        assertThrows(IllegalStateException.class, () -> sec.encrypt("test"));
    }

    @Test
    void decryptRequiresCmk() {
        assertThrows(IllegalStateException.class, () -> sec.decrypt(new byte[16], new byte[12]));
    }

    @Test
    void lockoutAfterThreeFailedAttempts() {
        sec.setupPin(pin("123456"));
        sec.lockNow();

        sec.verifyPin(pin("000000"));
        sec.verifyPin(pin("000000"));
        sec.verifyPin(pin("000000"));

        assertTrue(sec.isLockedOut());
        assertTrue(sec.lockoutRemainingMillis() > 0);
        assertEquals(0, sec.attemptsLeft());
    }

    @Test
    void clearLockoutRestoresAccess() {
        sec.setupPin(pin("123456"));
        sec.lockNow();

        sec.verifyPin(pin("000000"));
        sec.verifyPin(pin("000000"));
        sec.verifyPin(pin("000000"));
        assertTrue(sec.isLockedOut());

        sec.clearLockout();
        assertFalse(sec.isLockedOut());
    }

    @Test
    void changePinReWraps() {
        sec.setupPin(pin("123456"));
        String plain = "test data";
        SecurityService.Encrypted enc = sec.encrypt(plain);

        sec.changePin(null, pin("654321"));

        sec.lockNow();
        assertFalse(sec.verifyPin(pin("123456")));

        sec.clearLockout();
        assertTrue(sec.verifyPin(pin("654321")));
        assertEquals(plain, sec.decrypt(enc.cipher, enc.iv));
    }

    @Test
    void securityQuestionRecovery() {
        sec.setupPin(pin("123456"));
        String plain = "dato protegido";
        SecurityService.Encrypted enc = sec.encrypt(plain);

        sec.setSecurityQuestion("¿Color favorito?", "azul".toCharArray());
        assertTrue(sec.hasSecurityQuestion());
        assertEquals("¿Color favorito?", sec.getSecurityQuestion());

        sec.lockNow();

        assertTrue(sec.resetPinWithAnswer("azul".toCharArray(), pin("999999")));
        assertTrue(sec.isUnlocked());
        assertEquals(plain, sec.decrypt(enc.cipher, enc.iv));
    }

    @Test
    void wrongSecurityAnswerFails() {
        sec.setupPin(pin("123456"));
        sec.setSecurityQuestion("¿Color?", "azul".toCharArray());
        sec.lockNow();

        assertFalse(sec.resetPinWithAnswer("rojo".toCharArray(), pin("999999")));
    }

    @Test
    void recoveryCodeOneTime() {
        sec.setupPin(pin("123456"));
        String plain = "recovery test";
        SecurityService.Encrypted enc = sec.encrypt(plain);

        String code = sec.generateRecoveryCode();
        assertNotNull(code);
        assertTrue(code.contains("-"));
        assertTrue(sec.hasRecoveryCode());

        sec.lockNow();

        assertTrue(sec.resetPinWithRecoveryCode(code, pin("888888")));
        assertTrue(sec.isUnlocked());
        assertEquals(plain, sec.decrypt(enc.cipher, enc.iv));

        assertFalse(sec.hasRecoveryCode());
    }

    @Test
    void wrongRecoveryCodeFails() {
        sec.setupPin(pin("123456"));
        sec.generateRecoveryCode();
        sec.lockNow();

        assertFalse(sec.resetPinWithRecoveryCode("AAAA-BBBB-CCCC-DDDD", pin("888888")));
    }

    @Test
    void lockNowClearsCmk() {
        sec.setupPin(pin("123456"));
        assertTrue(sec.isUnlocked());

        sec.lockNow();
        assertFalse(sec.isUnlocked());
    }

    @Test
    void remainingAttemptsDecreases() {
        sec.setupPin(pin("123456"));
        sec.lockNow();

        assertEquals(3, sec.attemptsLeft());
        sec.verifyPin(pin("000000"));
        assertEquals(2, sec.attemptsLeft());
        sec.verifyPin(pin("000000"));
        assertEquals(1, sec.attemptsLeft());
    }

    @org.junit.jupiter.api.Test
    void lockoutPenaltyDoublesAndCapsAt24h() {
        assertEquals(30_000L, SecurityService.lockoutPenaltyMs(1));
        assertEquals(60_000L, SecurityService.lockoutPenaltyMs(2));
        assertEquals(24L * 60 * 60 * 1000, SecurityService.lockoutPenaltyMs(40));
        assertEquals(24L * 60 * 60 * 1000, SecurityService.lockoutPenaltyMs(Integer.MAX_VALUE));
    }
}
