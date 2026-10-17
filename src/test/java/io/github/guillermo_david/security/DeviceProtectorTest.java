package io.github.guillermo_david.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DeviceProtectorTest {

    @Test
    void plainValuesPassThrough() {
        assertEquals("hola", DeviceProtector.unprotect("hola"));
        assertNull(DeviceProtector.unprotect(null));
    }

    @Test
    void roundTripNeverLosesTheText() {
        assertEquals("¿Color favorito?", DeviceProtector.unprotect(DeviceProtector.protect("¿Color favorito?")));
    }

    @Test
    void protectedValueUnreadableOffWindowsReturnsNull() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win"))
            assertNull(DeviceProtector.unprotect("dpapi:AAAA"));
    }
}
