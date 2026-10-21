package vn.edu.usth.instaclone.data.repository

sealed class RegistrationException(message: String, cause: Throwable? = null)
    : Exception(message, cause) {
    class UsernameTaken : RegistrationException("Username already taken")
    class InvalidUsername : RegistrationException("Invalid username")
    class RecoveryRequired(cause: Throwable)
        : RegistrationException("Registration needs recovery", cause)
}