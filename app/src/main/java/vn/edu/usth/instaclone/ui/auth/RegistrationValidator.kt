package vn.edu.usth.instaclone.ui.auth

import java.util.Locale

data class RegistrationErrors(
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
) {
    val isValid: Boolean
        get() = email == null && username == null && password == null && confirmPassword == null
}

object RegistrationValidator {
    const val EMAIL_MESSAGE = "Enter a valid email"
    const val USERNAME_MESSAGE = "Use 3–30 letters, numbers, . or _"
    const val PASSWORD_MESSAGE = "Use at least 8 characters"
    const val CONFIRM_MESSAGE = "Passwords don't match"
    private val usernamePattern = Regex("^[a-z0-9_.]{3,30}$")
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun lowercaseUsername(value: String): String = value.lowercase(Locale.ROOT)
    fun normalizedUsername(value: String): String = lowercaseUsername(value.trim())
    fun validUsername(value: String): Boolean = usernamePattern.matches(value)
    fun validEmail(value: String): Boolean = emailPattern.matches(value.trim())

    fun validate(email: String, username: String, password: String, confirmPassword: String): RegistrationErrors =
        RegistrationErrors(
            email = if (validEmail(email)) null else EMAIL_MESSAGE,
            username = if (validUsername(normalizedUsername(username))) null else USERNAME_MESSAGE,
            password = if (password.length >= 8) null else PASSWORD_MESSAGE,
            confirmPassword = if (password == confirmPassword) null else CONFIRM_MESSAGE
        )
}