package vn.edu.usth.instaclone.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {Text("Log Out?")},
        text = {Text("You'll need to log in again to use InstaClone.")},
        confirmButton = {TextButton(onClick = onConfirm) {Text("Log out")}},
        dismissButton = {TextButton(onClick = onDismiss) {Text ("Cancel")}},
    )
}