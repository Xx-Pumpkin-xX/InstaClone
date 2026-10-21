package vn.edu.usth.instaclone.ui.auth

import java.util.Locale

object RegistrationValidator {
    fun normalizedUsername(value: String): String =
        value.trim().lowercase(Locale.ROOT)

    fun validUsername(value: String): Boolean =
        Regex("^[a-z0-9_]{3,20}$").matches(value)

    fun errorFor(
        email: String,
        username: String,
        password: String
    ): String? {
        val address = email.trim()

        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                .matches(address)) {
            return "Enter a valid email."
        }

        if (!validUsername(normalizedUsername(username))) {
            return "The username must be 3–20 characters long and consist of lowercase letters, numbers, or underscores (_)."
        }

        if (password.length < 10) {
            return "The password must be at least 10 characters long."
        }

        if (!password.any { it.isUpperCase() }) {
            return "The password must contain at least one uppercase letter."
        }

        if (!password.any {
                !it.isLetterOrDigit() && !it.isWhitespace() && !it.isISOControl()
            }) {
            return "The password must contain at least one special character, such as !, @, # or $."
        }

        return null
    }
}