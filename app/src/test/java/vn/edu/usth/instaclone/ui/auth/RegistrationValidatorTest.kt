package vn.edu.usth.instaclone.ui.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationValidatorTest {
    @Test fun normalizesUsernamesWithoutDependingOnDeviceLocale() {
        assertEquals("phong.demo", RegistrationValidator.normalizedUsername(" Phong.Demo "))
    }
    @Test fun acceptsDotsUnderscoresAndThirtyCharacterNames() {
        assertTrue(RegistrationValidator.validUsername("phong.demo"))
        assertTrue(RegistrationValidator.validUsername("phong_demo"))
        assertTrue(RegistrationValidator.validUsername("a".repeat(30)))
        assertTrue(RegistrationValidator.validUsername("abc"))
    }
    @Test fun rejectsShortLongAndUnsafeUsernames() {
        for (name in listOf("ab", "a".repeat(31), "a/b", "a b", "Dat", "đạt", "a@b")) {
            assertFalse(name, RegistrationValidator.validUsername(name))
        }
    }
    @Test fun emailErrorMatchesTeamCaseTwo() {
        assertEquals("Enter a valid email", RegistrationValidator.validate("abc", "phong", "abcdefgh", "abcdefgh").email)
    }
    @Test fun usernameErrorMatchesTeamCaseThree() {
        assertEquals("Use 3–30 letters, numbers, . or _", RegistrationValidator.validate("a@example.com", "ab", "abcdefgh", "abcdefgh").username)
    }
    @Test fun eightLowercaseCharactersAreAllowed() {
        assertTrue(RegistrationValidator.validate("a@example.com", "phong.demo", "abcdefgh", "abcdefgh").isValid)
    }
    @Test fun rejectsSevenCharacterPassword() {
        assertEquals("Use at least 8 characters", RegistrationValidator.validate("a@example.com", "phong", "abcdefg", "abcdefg").password)
    }
    @Test fun mismatchedConfirmationIsRejected() {
        assertEquals("Passwords don't match", RegistrationValidator.validate("a@example.com", "phong", "abcdefgh", "abcdefgi").confirmPassword)
    }
    @Test fun confirmationIsRequired() {
        assertEquals("Passwords don't match", RegistrationValidator.validate("a@example.com", "phong", "abcdefgh", "").confirmPassword)
    }
    @Test fun passwordsAreNotTrimmed() {
        assertEquals("Passwords don't match", RegistrationValidator.validate("a@example.com", "phong", "abcdefg ", "abcdefg").confirmPassword)
    }
}