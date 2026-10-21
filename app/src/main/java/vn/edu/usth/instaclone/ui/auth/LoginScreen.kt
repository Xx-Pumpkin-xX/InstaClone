package vn.edu.usth.instaclone.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * S-01 Login (UC-02). Draws the state; sends clicks to the ViewModel.
 * Navigation is passed IN as functions, so this screen doesn't know about routes.
 */

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("InstaClone", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = {Text("Email")},
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = {Text("Password")},
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = viewModel::openReset,
            modifier = Modifier.align(Alignment.End),
        ) {Text("Forgot password?")}
        state.errorMessage?.let {message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {viewModel.signIn(onSuccess = onLoggedIn)},
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)}
            else {Text("Log in")}
        }
        TextButton(onClick = onCreateAccount) {Text("Don't have an account? Sign up")}
        if (state.showReset) {
            ResetPasswordDialog(
                state = state,
                onEmailChange = viewModel::onResetEmailChange,
                onSend = viewModel::sendReset,
                onDismiss = viewModel::closeReset,
            )
        }
    }
}