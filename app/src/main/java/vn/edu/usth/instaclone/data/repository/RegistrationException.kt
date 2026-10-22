package vn.edu.usth.instaclone.data.repository

sealed class RegistrationException(message: String, cause: Throwable? = null)
    : Exception(message, cause) {
    class EmailAlreadyExists(cause: Throwable? = null) : RegistrationException("An account with this email already exists", cause)
    class NoConnection(cause: Throwable? = null) : RegistrationException("No connection. Check your network and try again.", cause)
    class UsernameTaken : RegistrationException("Username already taken")
    class InvalidUsername : RegistrationException("Invalid username")
    class RecoveryRequired(cause: Throwable)
        : RegistrationException("Registration needs recovery", cause)
}