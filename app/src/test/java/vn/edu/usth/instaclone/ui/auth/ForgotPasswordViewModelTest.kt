package vn.edu.usth.instaclone.ui.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeAuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class ResetRepository : AuthRepository by FakeAuthRepository() {
        var calls = 0
        var email: String? = null
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun sendPasswordReset(email: String) {
            calls++
            this.email = email
            gate?.await()
            failure?.let { throw it }
        }
    }

    @Test fun invalidEmailDoesNotCallRepository() = runTest(dispatcher) {
        val repo = ResetRepository()
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("invalid")
        vm.sendResetEmail()
        advanceUntilIdle()
        assertEquals(0, repo.calls)
        assertNotNull(vm.uiState.value.errorMessage)
    }
    @Test fun trimsEmailAndCompletesRequest() = runTest(dispatcher) {
        val repo = ResetRepository()
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange(" user@example.com ")
        vm.sendResetEmail()
        advanceUntilIdle()
        assertEquals("user@example.com", repo.email)
        assertTrue(vm.uiState.value.requestCompleted)
        assertFalse(vm.uiState.value.isLoading)
        vm.sendResetEmail()
        advanceUntilIdle()
        assertEquals(1, repo.calls)
    }
    @Test fun blocksDuplicateRequestWhileLoading() = runTest(dispatcher) {
        val repo = ResetRepository().apply { gate = CompletableDeferred() }
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("user@example.com")
        vm.sendResetEmail()
        vm.sendResetEmail()
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(1, repo.calls)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.requestCompleted)
    }
    @Test fun hidesRawErrorAndAllowsRetry() = runTest(dispatcher) {
        val repo = ResetRepository().apply { failure = Exception("private account information") }
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("user@example.com")
        vm.sendResetEmail()
        advanceUntilIdle()
        assertEquals("Unable to process the request. Please try again later.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.requestCompleted)
        repo.failure = null
        vm.sendResetEmail()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.requestCompleted)
    }
    @Test fun changingEmailClearsPreviousCompletion() = runTest(dispatcher) {
        val vm = ForgotPasswordViewModel(ResetRepository())
        vm.onEmailChange("one@example.com")
        vm.sendResetEmail()
        advanceUntilIdle()
        vm.onEmailChange("two@example.com")
        assertFalse(vm.uiState.value.requestCompleted)
        assertNull(vm.uiState.value.errorMessage)
    }
}