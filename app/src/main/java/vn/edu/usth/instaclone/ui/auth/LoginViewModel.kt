package vn.edu.usth.instaclone.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.AuthRepository

private  const val INVALID_EMAIL = "Enter a valid email"
private const val NO_CONNECTION = "No connection. Check your internet and try again."
private const val TOO_MANY = "Too many attempts. Try again later."

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    //Reset password dialog
    val showReset: Boolean = false,
    val resetEmail: String = "",
    val isSendingReset: Boolean = false,
    val resetError: String? = null,
    val resetDone: Boolean = false,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
    val canSendReset: Boolean
        get() = resetEmail.isNotBlank() && !isSendingReset
}

class LoginViewModel(
    private val authRepository: AuthRepository = AppContainer.authRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    //Log in (T-10)
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!isValidEmail(state.email)) {
            _uiState.update { it.copy(errorMessage = INVALID_EMAIL) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signIn(state.email, state.password)
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = messageFor(e)) }
            }
        }
    }
    //Reset password (T-05)
    fun openReset() = _uiState.update {
        it.copy(showReset = true, resetEmail = it.email, resetError = null, resetDone = false)
    }
    fun closeReset() = _uiState.update { it.copy(showReset = false) }
    fun onResetEmailChange(value: String) = _uiState.update { it.copy(resetEmail = value, resetError = null) }
    fun sendReset() {
        val email = _uiState.value.resetEmail.trim()
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(resetError = INVALID_EMAIL) }
            return
        }
        _uiState.update {it.copy(isSendingReset = true, resetError = null)}
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update {it.copy(isSendingReset = false, resetDone = true)}
            } catch (e: FirebaseNetworkException) {
                _uiState.update { it.copy(isSendingReset = false, resetError = NO_CONNECTION) }
            } catch (e: FirebaseTooManyRequestsException) {
                _uiState.update { it.copy(isSendingReset = false, resetError = TOO_MANY) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSendingReset = false, resetDone = true) }
            }
        }
    }
    private fun isValidEmail(email: String) = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    private fun messageFor(e: Exception):String = when (e) {
        is FirebaseNetworkException -> "No connection, Check your internet and try again."
        is FirebaseTooManyRequestsException -> "Too many request attempts. Try again later."
        is FirebaseAuthException -> "Incorrect email or password."
        else -> "Something went wrong. Try again."
    }
}