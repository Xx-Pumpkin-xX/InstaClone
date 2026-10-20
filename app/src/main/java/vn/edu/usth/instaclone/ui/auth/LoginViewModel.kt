package vn.edu.usth.instaclone.ui.auth

import android.util.Patterns
import androidx.lifecycle.Lifecycle
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

class LoginViewModel(
    private val authRepository: AuthRepository = AppContainer.authRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email") }
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
    private fun messageFor(e: Exception):String = when (e) {
        is FirebaseNetworkException -> "No connection, Check your internet and try again."
        is FirebaseTooManyRequestsException -> "Too many request attempts. Try again later."
        is FirebaseAuthException -> "Incorrect email or password."
        else -> "Something went wrong. Try again."
    }
}