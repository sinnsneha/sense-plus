package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CodewordRulesTest {

    @Test
    public void mixedCaseIsValid() {
        assertNull(CodewordRules.validate("Banana"));
    }

    @Test
    public void tooShortIsRejected() {
        assertNotNull(CodewordRules.validate("ab"));
    }

    @Test
    public void reservedWordIsRejected() {
        assertNotNull(CodewordRules.validate("help"));
        assertNotNull(CodewordRules.validate("okay"));
    }

    @Test
    public void digitsAreRejected() {
        assertNotNull(CodewordRules.validate("blue7"));
    }

    @Test
    public void surroundingWhitespaceIsIgnored() {
        assertNull(CodewordRules.validate("  pineapple  "));
    }

    @Test
    public void nullIsRejected() {
        assertNotNull(CodewordRules.validate(null));
    }
}
