package vn.edu.usth.instaclone.data

import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.ChatRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeChatRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseAuthRepository

object AppContainer {
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }

    // T-29/T-30 (Thinh). Switch to FirebaseChatRepository() once the screens work with fake data.
    val chatRepository: ChatRepository by lazy { FakeChatRepository() }
}