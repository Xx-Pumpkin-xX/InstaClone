package vn.edu.usth.instaclone.data

import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.ChatRepository
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.FakeCommentRepository
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.FakeLikeRepository
import vn.edu.usth.instaclone.data.repository.FirebaseImageStorage
import vn.edu.usth.instaclone.data.repository.FirebasePostRepository
import vn.edu.usth.instaclone.data.repository.FollowRepository
import vn.edu.usth.instaclone.data.repository.ImageStorage
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseChatRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseAuthRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseFollowRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseUserRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseCommentRepository

object AppContainer {
    private val fakePostRepository by lazy { FakePostRepository() }
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
    val chatRepository: ChatRepository by lazy { FirebaseChatRepository() }
    val postRepository: PostRepository by lazy { FirebasePostRepository(imageStorage) }
    val userRepository: UserRepository by lazy { FirebaseUserRepository() }
    val followRepository: FollowRepository by lazy { FirebaseFollowRepository() }
    val imageStorage: ImageStorage by lazy { FirebaseImageStorage() }
    val profileImageStorage: ImageStorage get() = imageStorage
    val commentRepository: CommentRepository by lazy { FirebaseCommentRepository() }
    val likeRepository: LikeRepository by lazy { FakeLikeRepository(fakePostRepository) }
}