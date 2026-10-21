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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import vn.edu.usth.instaclone.data.repository.RegistrationException
import vn.edu.usth.instaclone.data.repository.fake.FakeAuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun ready(repo: FakeAuthRepository): RegisterViewModel =
        RegisterViewModel(repo).apply {
            onEmailChange(" dat@example.com ")
            onUsernameChange(" Dat_01 ")
            onPasswordChange("Secret123!")
        }

    @Test fun invalidInputDoesNotCallRepository() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = RegisterViewModel(repo)
        vm.register()
        advanceUntilIdle()
        assertEquals(0, repo.signUpCalls)
        assertTrue(vm.uiState.value.errorMessage != null)
    }
    @Test fun loadingAndDuplicateSubmit() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository().apply { signUpGate = gate }
        val vm = ready(repo)
        vm.register()
        assertTrue(vm.uiState.value.isLoading)
        vm.register()
        runCurrent()
        assertEquals(1, repo.signUpCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.registered)
    }
    @Test fun successNormalizesInputAndClearsPassword() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ready(repo)
        vm.register()
        advanceUntilIdle()
        assertEquals("dat@example.com", repo.lastEmail)
        assertEquals("dat_01", repo.lastUsername)
        assertEquals("", vm.uiState.value.password)
        assertTrue(vm.uiState.value.registered)
    }
    @Test fun usernameTakenShowsError() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply {
            signUpError = RegistrationException.UsernameTaken()
        }
        val vm = ready(repo)
        vm.register()
        advanceUntilIdle()
        assertEquals("Username has been used.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.registered)
        assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun recoveryRequiredBlocksFurtherSubmit() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply {
            signUpError = RegistrationException.RecoveryRequired(Exception("test"))
        }
        val vm = ready(repo)
        vm.register()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recoveryRequired)
        vm.register()
        advanceUntilIdle()
        assertEquals(1, repo.signUpCalls)
    }
}