package vn.edu.usth.instaclone.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.RegistrationException

data class RegisterUiState(
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registered: Boolean = false,
    val recoveryRequired: Boolean = false
) {
    val canSubmit: Boolean
        get() = !isLoading && !registered && !recoveryRequired
}

class RegisterViewModel(
    private val authRepository: AuthRepository = AppContainer.authRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        if (_uiState.value.canSubmit) {
            _uiState.update { it.copy(email = value, errorMessage = null) }
        }
    }
    fun onUsernameChange(value: String) {
        if (_uiState.value.canSubmit) {
            _uiState.update { it.copy(username = value, errorMessage = null) }
        }
    }
    fun onPasswordChange(value: String) {
        if (_uiState.value.canSubmit) {
            _uiState.update { it.copy(password = value, errorMessage = null) }
        }
    }

    fun register() {
        val input = _uiState.value
        if (!input.canSubmit) return
        val validationError = RegistrationValidator.errorFor(
            input.email, input.username, input.password
        )
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signUp(
                    input.email.trim(),
                    RegistrationValidator.normalizedUsername(input.username),
                    input.password
                )
                _uiState.update { it.copy(registered = true, password = "") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = messageFor(error),
                        recoveryRequired = error is RegistrationException.RecoveryRequired
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun messageFor(error: Exception): String = when (error) {
        is RegistrationException.UsernameTaken
            -> "Username has been used."
        is RegistrationException.InvalidUsername
            -> "Invalid username."
        is RegistrationException.RecoveryRequired
            -> "Registration completion has not been confirmed. You must verify and recover your account before proceeding."
        is FirebaseNetworkException
            -> "No connection. Check your wifi and retry."
        is FirebaseTooManyRequestsException
            -> "Too many request. Please retry."
        is FirebaseAuthException
            -> "Can not create account. Check your username and password."
        else
            -> "Sign-in failed. Please retry."
    }
}