package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RegistrationRollbackTest {
    @Test fun rejectedClaimDeletesOnlyTheNewAccount() = runTest {
        val accounts = mutableSetOf("existing-user", "new-user")
        RegistrationRollback.deleteCreatedAccount(RegistrationException.UsernameTaken()) {
            accounts.remove("new-user")
        }
        assertEquals(setOf("existing-user"), accounts)
    }
    @Test fun cleanupFailureIsNotReportedAsSuccessfulRollback() = runTest {
        val original = RegistrationException.UsernameTaken()
        val cleanup = IllegalStateException("delete failed")
        try {
            RegistrationRollback.deleteCreatedAccount(original) { throw cleanup }
            fail("Expected recovery-required error")
        } catch (error: RegistrationException.RecoveryRequired) {
            assertSame(original, error.cause)
            assertEquals(cleanup.javaClass, error.suppressed.single().javaClass)
            assertEquals(cleanup.message, error.suppressed.single().message)
        }
    }
}