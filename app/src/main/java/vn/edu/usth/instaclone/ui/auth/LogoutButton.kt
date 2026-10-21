package vn.edu.usth.instaclone.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// A logout button that asks for confirmation first. Does not sign out by itself
// Stay in profile menu of Trang Task 23 work.

@Composable
fun LogoutButton(
    onConfirmLogout: () ->Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showDialog = true}, modifier = modifier) {
        Text("Log out")
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {showDialog =false},
            title = {Text("Log out?")},
            text = {Text("You'll need to log in again to use InstaClone.")},
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onConfirmLogout()
                }) {Text ("Log Out")}
            },
            dismissButton = {
                TextButton(onClick = {showDialog = false}) {Text("Cancel")}
            },
        )
    }
}