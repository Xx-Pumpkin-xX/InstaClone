package vn.edu.usth.instaclone.ui.auth

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBackToLogin: () -> Unit, vm: RegisterViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    BackHandler(enabled = state.isLoading || state.recoveryRequired) { }
    LaunchedEffect(state.registered) { if (state.registered) onRegistered() }
    RegisterContent(state, vm::onEmailChange, vm::onUsernameChange, vm::onPasswordChange,
        vm::onConfirmPasswordChange, vm::register, onBackToLogin)
}

@Composable
fun RegisterContent(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = state.email, onValueChange = onEmailChange,
            label = { Text("Email") }, singleLine = true, enabled = state.canSubmit,
            isError = state.errors.email != null,
            supportingText = { state.errors.email?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.username, onValueChange = onUsernameChange,
            label = { Text("Username") }, singleLine = true, enabled = state.canSubmit,
            isError = state.errors.username != null,
            supportingText = { Text(state.errors.username ?: RegistrationValidator.USERNAME_MESSAGE) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password, onValueChange = onPasswordChange,
            label = { Text("Password") }, singleLine = true, enabled = state.canSubmit,
            isError = state.errors.password != null,
            supportingText = { Text(state.errors.password ?: RegistrationValidator.PASSWORD_MESSAGE) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.confirmPassword, onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm password") }, singleLine = true, enabled = state.canSubmit,
            isError = state.errors.confirmPassword != null,
            supportingText = { state.errors.confirmPassword?.let { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.recoveryRequired) {
            Text("Account verification is required before continuing. Ask the team to verify the account state before signing in again.")
        }
        Button(onClick = onRegister, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Sign up")
        }
        TextButton(onClick = onBackToLogin, enabled = !state.isLoading && !state.recoveryRequired) {
            Text("Already have an account? Login")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    InstaCloneTheme { RegisterContent(RegisterUiState(), {}, {}, {}, {}, {}, {}) }
}