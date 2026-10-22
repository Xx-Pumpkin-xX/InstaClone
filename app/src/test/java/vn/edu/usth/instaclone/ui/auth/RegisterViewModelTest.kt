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
import vn.edu.usth.instaclone.data.repository.RegistrationException
import vn.edu.usth.instaclone.data.repository.fake.FakeAuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun ready(repo: FakeAuthRepository) = RegisterViewModel(repo).apply {
        onEmailChange(" dat@example.com ")
        onUsernameChange("Phong.Demo")
        onPasswordChange("abcdefgh")
        onConfirmPasswordChange("abcdefgh")
    }
    @Test fun invalidEmailStaysUnderEmailAndSendsNothing() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ready(repo)
        vm.onEmailChange("abc")
        vm.register()
        advanceUntilIdle()
        assertEquals("Enter a valid email", vm.uiState.value.errors.email)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(0, repo.signUpCalls)
    }
    @Test fun invalidUsernameStaysUnderUsername() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ready(repo)
        vm.onUsernameChange("ab")
        vm.register()
        assertEquals("Use 3–30 letters, numbers, . or _", vm.uiState.value.errors.username)
        assertEquals(0, repo.signUpCalls)
    }
    @Test fun uppercaseIsLowercasedWhileTyping() {
        val vm = ready(FakeAuthRepository())
        vm.onUsernameChange("PhongDemo")
        assertEquals("phongdemo", vm.uiState.value.username)
    }
    @Test fun mismatchedConfirmationDoesNotSend() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ready(repo)
        vm.onConfirmPasswordChange("different")
        vm.register()
        assertEquals("Passwords don't match", vm.uiState.value.errors.confirmPassword)
        assertEquals(0, repo.signUpCalls)
    }
    @Test fun blocksDuplicateWhilePending() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository().apply { signUpGate = gate }
        val vm = ready(repo)
        vm.register()
        vm.register()
        assertTrue(vm.uiState.value.isLoading)
        runCurrent()
        assertEquals(1, repo.signUpCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.registered)
    }
    @Test fun acceptsDottedUsernameAndEightLowercasePassword() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ready(repo)
        vm.register()
        advanceUntilIdle()
        assertEquals("dat@example.com", repo.lastEmail)
        assertEquals("phong.demo", repo.lastUsername)
        assertTrue(vm.uiState.value.registered)
        assertEquals("", vm.uiState.value.password)
        assertEquals("", vm.uiState.value.confirmPassword)
    }
    @Test fun usernameTakenHasExactFieldMessage() = runTest(dispatcher) {
        val vm = ready(FakeAuthRepository().apply { signUpError = RegistrationException.UsernameTaken() })
        vm.register()
        advanceUntilIdle()
        assertEquals("Username is taken", vm.uiState.value.errors.username)
        assertFalse(vm.uiState.value.registered)
        assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun duplicateEmailHasExactFieldMessage() = runTest(dispatcher) {
        val vm = ready(FakeAuthRepository().apply {
            signUpError = RegistrationException.EmailAlreadyExists()
        })
        vm.register()
        advanceUntilIdle()
        assertEquals("An account with this email already exists", vm.uiState.value.errors.email)
        assertNull(vm.uiState.value.errorMessage)
    }
    @Test fun offlineKeepsAllInput() = runTest(dispatcher) {
        val vm = ready(FakeAuthRepository().apply { signUpError = RegistrationException.NoConnection() })
        vm.register()
        advanceUntilIdle()
        assertEquals("No connection. Check your network and try again.", vm.uiState.value.errorMessage)
        assertEquals("abcdefgh", vm.uiState.value.password)
        assertEquals("abcdefgh", vm.uiState.value.confirmPassword)
        assertEquals("phong.demo", vm.uiState.value.username)
    }
    @Test fun recoveryBlocksFurtherRequestsWithoutExposingInternalError() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { signUpError = RegistrationException.RecoveryRequired(Exception("private")) }
        val vm = ready(repo)
        vm.register()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recoveryRequired)
        assertEquals("Couldn't finish sign-up. Try again", vm.uiState.value.errorMessage)
        vm.register()
        advanceUntilIdle()
        assertEquals(1, repo.signUpCalls)
    }
    @Test fun genericFailureUsesTeamMessageAndKeepsInput() = runTest(dispatcher) {
        val vm = ready(FakeAuthRepository().apply { signUpError = Exception("private") })
        vm.register()
        advanceUntilIdle()
        assertEquals("Couldn't finish sign-up. Try again", vm.uiState.value.errorMessage)
        assertEquals("abcdefgh", vm.uiState.value.confirmPassword)
    }
}