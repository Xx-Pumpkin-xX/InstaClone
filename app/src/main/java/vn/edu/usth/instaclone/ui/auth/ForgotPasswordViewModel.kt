package vn.edu.usth.instaclone.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.AuthRepository

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val requestCompleted: Boolean = false,
    val errorMessage: String? = null
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AppContainer.authRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(email = value, requestCompleted = false, errorMessage = null)
        }
    }

    fun sendResetEmail() {
        val state = _uiState.value
        if (state.isLoading || state.requestCompleted) return
        val email = state.email.trim()
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update { it.copy(requestCompleted = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is FirebaseNetworkException -> "No connection. Check your network and try again."
                    is FirebaseTooManyRequestsException -> "Too many requests. Please try again later."
                    else -> "Unable to process the request. Please try again later."
                }
                _uiState.update { it.copy(errorMessage = message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}