package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VoiceCommandParserTest {

    @Test
    public void nullAndEmptyAreNone() {
        assertEquals(VoiceCommandParser.Command.NONE, VoiceCommandParser.parse(null));
        assertEquals(VoiceCommandParser.Command.NONE, VoiceCommandParser.parse(""));
        assertEquals(VoiceCommandParser.Command.NONE, VoiceCommandParser.parse("   "));
    }

    @Test
    public void helpAndOkayKeywords() {
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("help"));
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("please help me"));
        assertEquals(VoiceCommandParser.Command.OKAY, VoiceCommandParser.parse("i'm okay"));
        assertEquals(VoiceCommandParser.Command.OKAY, VoiceCommandParser.parse("yes"));
        assertEquals(VoiceCommandParser.Command.OKAY, VoiceCommandParser.parse("alright fine"));
    }

    @Test
    public void negatedFineIsHelp() {
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("not fine"));
    }

    @Test
    public void helpBeatsOkayWord() {
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("yes help"));
    }

    @Test
    public void alrightAloneIsOkay() {
        assertEquals(VoiceCommandParser.Command.OKAY, VoiceCommandParser.parse("alright"));
    }

    @Test
    public void substringsDoNotTrigger() {
        assertEquals(VoiceCommandParser.Command.NONE, VoiceCommandParser.parse("helpful"));
        assertEquals(VoiceCommandParser.Command.NONE, VoiceCommandParser.parse("okays"));
    }

    @Test
    public void negationBeforeOkayWordIsHelp() {
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("not okay"));
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("no, okay"));
        assertEquals(VoiceCommandParser.Command.HELP, VoiceCommandParser.parse("i'm not okay"));
    }

    @Test
    public void codewordAloneMatches() {
        assertEquals(VoiceCommandParser.Command.CODEWORD,
                VoiceCommandParser.parse("banana", "banana"));
    }

    @Test
    public void codewordInsideSentenceMatches() {
        assertEquals(VoiceCommandParser.Command.CODEWORD,
                VoiceCommandParser.parse("please banana now", "banana"));
    }

    @Test
    public void wordContainingCodewordDoesNotMatch() {
        assertEquals(VoiceCommandParser.Command.NONE,
                VoiceCommandParser.parse("bluebird", "blue"));
    }

    @Test
    public void nullCodewordNeverMatches() {
        assertEquals(VoiceCommandParser.Command.NONE,
                VoiceCommandParser.parse("banana", null));
        assertEquals(VoiceCommandParser.Command.NONE,
                VoiceCommandParser.parse("banana", ""));
    }

    @Test
    public void negationStillHelpWhenCodewordSet() {
        assertEquals(VoiceCommandParser.Command.HELP,
                VoiceCommandParser.parse("not okay", "banana"));
    }
}
