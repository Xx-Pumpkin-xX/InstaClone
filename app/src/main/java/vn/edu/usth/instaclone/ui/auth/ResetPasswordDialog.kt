package vn.edu.usth.instaclone.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ResetPasswordDialog(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {Text("Reset password.")},
        text = {
            if (state.resetDone) {
                Text("If an account exists for this email, we've sent a reset link")
            }
            else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your account's email and we'll send you a link to choose a new password")
                    OutlinedTextField(
                        value = state.resetEmail,
                        onValueChange = onEmailChange,
                        label = {Text("Email")},
                        singleLine = true,
                        isError = state.resetError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done,),
                    )
                    state.resetError?.let {Text(it, color = MaterialTheme.colorScheme.error)}
                }
            }
        },
        confirmButton = {
            if (state.resetDone) {
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                TextButton(onClick = onSend, enabled = state.canSendReset) {
                    if (state.isSendingReset) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    else {
                        Text("Send")
                    }
                }
            }
        },
        //After sending there's nothing to cancel, so only "OK" remains
        dismissButton = if (state.resetDone) null else {
            {TextButton(onClick = onDismiss) {Text("Cancel") }}
        },
    )
}