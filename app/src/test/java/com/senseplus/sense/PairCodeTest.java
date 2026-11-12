package com.senseplus.sense;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PairCodeTest {

    /** A Random that always returns the same number, so the output is known. */
    private static Random fixed(final int value) {
        return new Random() {
            @Override public int nextInt(int bound) { return value; }
        };
    }

    @Test
    public void generatedCodeIsSixDigits() {
        Random r = new Random(7);
        for (int i = 0; i < 1000; i++) {
            String code = PairCode.generate(r);
            assertEquals(6, code.length());
            assertTrue(code, PairCode.isValidFormat(code));
        }
    }

    @Test
    public void leadingZerosAreKept() {
        assertEquals("000042", PairCode.generate(fixed(42)));
        assertEquals("000000", PairCode.generate(fixed(0)));
        assertEquals("999999", PairCode.generate(fixed(999_999)));
    }

    @Test
    public void formatValidation() {
        assertTrue(PairCode.isValidFormat("012345"));
        assertFalse(PairCode.isValidFormat("12345"));
        assertFalse(PairCode.isValidFormat("1234567"));
        assertFalse(PairCode.isValidFormat("12a456"));
        assertFalse(PairCode.isValidFormat(" 23456"));
        assertFalse(PairCode.isValidFormat(null));
    }

    @Test
    public void normalizeAcceptsSpacesAndDashes() {
        assertEquals("123456", PairCode.normalize("123 456"));
        assertEquals("123456", PairCode.normalize(" 123-456 "));
        assertEquals("123456", PairCode.normalize("123456"));
        assertNull(PairCode.normalize("12345"));
        assertNull(PairCode.normalize("abc def"));
        assertNull(PairCode.normalize(null));
    }

    @Test
    public void displayAndSpokenForms() {
        assertEquals("123 456", PairCode.display("123456"));
        assertEquals("0, 4, 2, 0, 0, 7", PairCode.spoken("042007"));
    }
}
