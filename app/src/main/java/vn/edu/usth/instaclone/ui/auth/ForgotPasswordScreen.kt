package vn.edu.usth.instaclone.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    vm: ForgotPasswordViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    ForgotPasswordContent(
        state = state,
        onEmailChange = vm::onEmailChange,
        onSend = vm::sendResetEmail,
        onBackToLogin = onBackToLogin
    )
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Reset password", style = MaterialTheme.typography.headlineLarge)
        Text("Enter your email to request a password reset link.")
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (state.requestCompleted) {
            // Same message whether or not this email has an account.
            Text("If an account exists for this email, we've sent a reset link")
        }
        Button(
            onClick = onSend,
            enabled = !state.isLoading && !state.requestCompleted,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                )
            } else { Text("Send reset link") }
        }
        TextButton(onClick = onBackToLogin) { Text("Back to login") }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordPreview() {
    InstaCloneTheme {
        ForgotPasswordContent(ForgotPasswordUiState(), {}, {}, {})
    }
}