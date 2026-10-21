package vn.edu.usth.instaclone.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationValidatorTest {
    @Test fun usernameIsNormalized() {
        assertEquals("dat_01", RegistrationValidator.normalizedUsername(" Dat_01 "))
    }
    @Test fun usernameLengthBoundaries() {
        assertFalse(RegistrationValidator.validUsername("ab"))
        assertTrue(RegistrationValidator.validUsername("abc"))
        assertTrue(RegistrationValidator.validUsername("a".repeat(20)))
        assertFalse(RegistrationValidator.validUsername("a".repeat(21)))
    }
    @Test fun usernameRejectsInvalidCharacters() {
        assertFalse(RegistrationValidator.validUsername("dat.name"))
        assertFalse(RegistrationValidator.validUsername("dat name"))
        assertFalse(RegistrationValidator.validUsername("Dat"))
    }
    @Test fun validInputHasNoError() {
        assertNull(RegistrationValidator.errorFor("dat@example.com", "dat", "Secret123!"))
    }
    @Test fun invalidEmailHasError() {
        assertTrue(RegistrationValidator.errorFor("invalid", "dat", "Secret123!") != null)
    }
    @Test fun shortPasswordHasError() {
        assertTrue(RegistrationValidator.errorFor("dat@example.com", "dat", "123") != null)
    }
    @Test fun passwordAtLeastTenCharacters() {
        assertTrue(RegistrationValidator.errorFor("dat@example.com", "dat", "Short12!A") != null)
        assertNull(RegistrationValidator.errorFor("dat@example.com", "dat", "Secret123!"))
    }
    @Test fun passwordRequiresUppercase() {
        assertTrue(RegistrationValidator.errorFor("dat@example.com", "dat", "secret123!") != null)
    }
    @Test fun passwordRequiresSpecialCharacter() {
        assertTrue(RegistrationValidator.errorFor("dat@example.com", "dat", "Secret1234") != null)
    }
    @Test fun spaceDoesNotCountAsSpecialCharacter() {
        assertTrue(RegistrationValidator.errorFor("dat@example.com", "dat", "Secret123 ") != null)
    }
}