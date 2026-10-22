package vn.edu.usth.instaclone.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errors: RegistrationErrors = RegistrationErrors(),
    val errorMessage: String? = null,
    val registered: Boolean = false,
    val recoveryRequired: Boolean = false
) {
    val canSubmit: Boolean get() = !isLoading && !registered && !recoveryRequired
}

class RegisterViewModel(
    private val authRepository: AuthRepository = AppContainer.authRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        if (_uiState.value.canSubmit) _uiState.update {
            it.copy(email = value, errors = it.errors.copy(email = null), errorMessage = null)
        }
    }
    fun onUsernameChange(value: String) {
        if (_uiState.value.canSubmit) _uiState.update {
            it.copy(username = RegistrationValidator.lowercaseUsername(value),
                errors = it.errors.copy(username = null), errorMessage = null)
        }
    }
    fun onPasswordChange(value: String) {
        if (_uiState.value.canSubmit) _uiState.update {
            it.copy(password = value, errors = it.errors.copy(password = null, confirmPassword = null), errorMessage = null)
        }
    }
    fun onConfirmPasswordChange(value: String) {
        if (_uiState.value.canSubmit) _uiState.update {
            it.copy(confirmPassword = value, errors = it.errors.copy(confirmPassword = null), errorMessage = null)
        }
    }

    fun register() {
        val input = _uiState.value
        if (!input.canSubmit) return
        val errors = RegistrationValidator.validate(input.email, input.username, input.password, input.confirmPassword)
        if (!errors.isValid) {
            _uiState.update { it.copy(errors = errors, errorMessage = null) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errors = RegistrationErrors(), errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signUp(input.email.trim(), RegistrationValidator.normalizedUsername(input.username), input.password)
                _uiState.update { it.copy(registered = true, password = "", confirmPassword = "") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = messageFor(error)
                _uiState.update {
                    when {
                        error is RegistrationException.UsernameTaken -> it.copy(errors = it.errors.copy(username = message))
                        error is RegistrationException.InvalidUsername -> it.copy(errors = it.errors.copy(username = message))
                        error is RegistrationException.EmailAlreadyExists ||
                            (error is FirebaseAuthException && error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") ->
                            it.copy(errors = it.errors.copy(email = message))
                        error is FirebaseAuthException && error.errorCode == "ERROR_INVALID_EMAIL" ->
                            it.copy(errors = it.errors.copy(email = message))
                        error is FirebaseAuthException && error.errorCode == "ERROR_WEAK_PASSWORD" ->
                            it.copy(errors = it.errors.copy(password = RegistrationValidator.PASSWORD_MESSAGE))
                        else -> it.copy(errorMessage = message, recoveryRequired = error is RegistrationException.RecoveryRequired)
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun messageFor(error: Exception): String = when {
        error is RegistrationException.UsernameTaken -> "Username is taken"
        error is RegistrationException.InvalidUsername -> RegistrationValidator.USERNAME_MESSAGE
        error is RegistrationException.NoConnection || error is FirebaseNetworkException -> "No connection. Check your network and try again."
        error is RegistrationException.EmailAlreadyExists ||
            (error is FirebaseAuthException && error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") -> "An account with this email already exists"
        error is FirebaseAuthException && error.errorCode == "ERROR_INVALID_EMAIL" -> RegistrationValidator.EMAIL_MESSAGE
        else -> "Couldn't finish sign-up. Try again"
    }
}