package com.senseplus.sense;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PhraseRulesTest {

    @Test
    public void emptyOrWhitespaceIsRejected() {
        assertNotNull(PhraseRules.validate("", Collections.emptyList()));
        assertNotNull(PhraseRules.validate("   ", Collections.emptyList()));
        assertNotNull(PhraseRules.validate(null, Collections.emptyList()));
    }

    @Test
    public void tooLongIsRejected() {
        StringBuilder phrase = new StringBuilder();
        for (int i = 0; i < 61; i++) phrase.append('a');
        assertNotNull(PhraseRules.validate(phrase.toString(), Collections.emptyList()));
    }

    @Test
    public void sixtyCharactersIsAccepted() {
        StringBuilder phrase = new StringBuilder();
        for (int i = 0; i < 60; i++) phrase.append('a');
        assertNull(PhraseRules.validate(phrase.toString(), Collections.emptyList()));
    }

    @Test
    public void duplicateOfDefaultIgnoringCaseIsRejected() {
        assertNotNull(PhraseRules.validate("hElP", Collections.emptyList()));
        assertNotNull(PhraseRules.validate("PLEASE CALL MY GUARDIAN", Collections.emptyList()));
    }

    @Test
    public void duplicateOfCustomIgnoringCaseIsRejected() {
        List<String> existing = new ArrayList<>();
        existing.add("Please call my sister");
        assertNotNull(PhraseRules.validate("please CALL my sister", existing));
    }

    @Test
    public void twentyFirstCustomPhraseIsRejected() {
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < PhraseRules.MAX_CUSTOM; i++) existing.add("Phrase " + i);
        assertNotNull(PhraseRules.validate("One more", existing));
    }

    @Test
    public void validNewPhraseIsAccepted() {
        assertNull(PhraseRules.validate("Please call my sister", Collections.emptyList()));
    }

    @Test
    public void surroundingWhitespaceIsTrimmedBeforeValidation() {
        assertNull(PhraseRules.validate("  Please call my sister  ", Collections.emptyList()));
        assertNotNull(PhraseRules.validate("  help  ", Collections.emptyList()));
    }
}
