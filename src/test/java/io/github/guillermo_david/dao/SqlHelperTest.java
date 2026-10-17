package io.github.guillermo_david.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SqlHelperTest {

    @Test
    void escNullReturnsNULL() {
        assertEquals("NULL", SqlHelper.esc(null));
    }

    @Test
    void escPlainString() {
        assertEquals("'hello'", SqlHelper.esc("hello"));
    }

    @Test
    void escEscapesSingleQuotes() {
        assertEquals("'it''s a test'", SqlHelper.esc("it's a test"));
    }

    @Test
    void escStripsNulChars() {
        assertEquals("'ab'", SqlHelper.esc("a\u0000b"));
    }

    @Test
    void escNeutralizesInjectionPayload() {
        assertEquals("'x''); DROP TABLE pildoras;--'", SqlHelper.esc("x'); DROP TABLE pildoras;--"));
    }

    @Test
    void escEmptyString() {
        assertEquals("''", SqlHelper.esc(""));
    }

    @Test
    void escBlobNullReturnsNULL() {
        assertEquals("NULL", SqlHelper.escBlob(null));
    }

    @Test
    void escBlobEncodesBytes() {
        byte[] data = {(byte) 0xCA, (byte) 0xFE, (byte) 0x00, (byte) 0xFF};
        assertEquals("X'cafe00ff'", SqlHelper.escBlob(data));
    }

    @Test
    void escBlobEmptyArray() {
        assertEquals("X''", SqlHelper.escBlob(new byte[0]));
    }

    @Test
    void hexToBytesNullReturnsNull() {
        assertNull(SqlHelper.hexToBytes(null));
    }

    @Test
    void hexToBytesBlankReturnsNull() {
        assertNull(SqlHelper.hexToBytes(""));
        assertNull(SqlHelper.hexToBytes("   "));
    }

    @Test
    void hexToBytesDecodesCorrectly() {
        byte[] expected = {(byte) 0xCA, (byte) 0xFE, (byte) 0x00, (byte) 0xFF};
        assertArrayEquals(expected, SqlHelper.hexToBytes("CAFE00FF"));
    }

    @Test
    void hexToBytesLowerCase() {
        byte[] expected = {(byte) 0xAB, (byte) 0xCD};
        assertArrayEquals(expected, SqlHelper.hexToBytes("abcd"));
    }

    @Test
    void escBlobAndHexToBytesRoundTrip() {
        byte[] original = {0x01, 0x02, (byte) 0xAA, (byte) 0xBB};
        String escaped = SqlHelper.escBlob(original);
        String hex = escaped.substring(2, escaped.length() - 1); // strip X' and '
        assertArrayEquals(original, SqlHelper.hexToBytes(hex));
    }

    @Test
    void parseDateTimeNullReturnsNull() {
        assertNull(SqlHelper.parseDateTime(null));
    }

    @Test
    void parseDateTimeBlankReturnsNull() {
        assertNull(SqlHelper.parseDateTime(""));
        assertNull(SqlHelper.parseDateTime("   "));
    }

    @Test
    void parseDateTimeStandardFormat() {
        LocalDateTime expected = LocalDateTime.of(2024, 3, 15, 10, 30, 45);
        assertEquals(expected, SqlHelper.parseDateTime("2024-03-15 10:30:45"));
    }

    @Test
    void parseDateTimeWithTSeparator() {
        LocalDateTime expected = LocalDateTime.of(2024, 3, 15, 10, 30, 45);
        assertEquals(expected, SqlHelper.parseDateTime("2024-03-15T10:30:45"));
    }

    @Test
    void parseDateTimeInvalidReturnsNull() {
        assertNull(SqlHelper.parseDateTime("not-a-date"));
    }
}
