package vn.edu.usth.instaclone.data

import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseAuthRepository

object AppContainer {
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
}