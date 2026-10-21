package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** Only invoke when the Firestore transaction is known NOT to have committed. */
object RegistrationRollback {
    suspend fun deleteCreatedAccount(originalError: Exception, delete: suspend () -> Unit) {
        try {
            withContext(NonCancellable) {
                withTimeout(10.seconds) { delete() }
            }
        } catch (cleanupError: Exception) {
            throw RegistrationException.RecoveryRequired(originalError).also {
                it.addSuppressed(cleanupError)
            }
        }
        currentCoroutineContext().ensureActive()
    }
}