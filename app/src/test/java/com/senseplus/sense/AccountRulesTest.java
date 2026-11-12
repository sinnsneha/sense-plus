package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AccountRulesTest {

    @Test
    public void validEmailAndPasswordPass() {
        assertNull(AccountRules.emailError("asha@example.com"));
        assertNull(AccountRules.passwordError("secret1"));
    }

    @Test
    public void badEmailIsRejected() {
        assertNotNull(AccountRules.emailError("not-an-email"));
        assertNotNull(AccountRules.emailError("missing@domain"));
        assertNotNull(AccountRules.emailError(""));
        assertNotNull(AccountRules.emailError(null));
    }

    @Test
    public void fiveCharacterPasswordIsRejected() {
        assertNotNull(AccountRules.passwordError("12345"));
        assertNotNull(AccountRules.passwordError(""));
        assertNotNull(AccountRules.passwordError(null));
        assertNull(AccountRules.passwordError("123456"));
    }

    @Test
    public void mismatchedConfirmationIsRejected() {
        assertNotNull(AccountRules.confirmError("secret1", "secret2"));
        assertNotNull(AccountRules.confirmError("secret1", null));
        assertNull(AccountRules.confirmError("secret1", "secret1"));
    }

    @Test
    public void emptyNameIsRejected() {
        assertNotNull(AccountRules.nameError(""));
        assertNotNull(AccountRules.nameError("   "));
        assertNotNull(AccountRules.nameError(null));
        assertNull(AccountRules.nameError("Asha"));
    }

    @Test
    public void fortyOneCharacterNameIsRejected() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 41; i++) name.append('a');
        assertNotNull(AccountRules.nameError(name.toString()));
        assertNull(AccountRules.nameError(name.substring(0, 40)));
    }

    @Test
    public void emptyPhoneIsAllowed() {
        assertNull(AccountRules.phoneError(""));
        assertNull(AccountRules.phoneError("   "));
        assertNull(AccountRules.phoneError(null));
    }

    @Test
    public void phoneWithLettersIsRejected() {
        assertNotNull(AccountRules.phoneError("abc123"));
        assertNotNull(AccountRules.phoneError("+91 98-76"));
        assertNull(AccountRules.phoneError("+91 98765 43210"));
    }

    @Test
    public void phoneLongerThanTwentyCharactersIsRejected() {
        StringBuilder phone = new StringBuilder();
        for (int i = 0; i < 21; i++) phone.append('9');
        assertNotNull(AccountRules.phoneError(phone.toString()));
    }
}
