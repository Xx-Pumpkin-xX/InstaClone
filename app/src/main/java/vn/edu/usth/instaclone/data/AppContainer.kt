package vn.edu.usth.instaclone.data

import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.ChatRepository
import vn.edu.usth.instaclone.data.repository.FakeImageStorage
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.FollowRepository
import vn.edu.usth.instaclone.data.repository.ImageStorage
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeChatRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeFollowRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeUserRepository
import vn.edu.usth.instaclone.data.repository.firebase.FirebaseAuthRepository
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.FakeCommentRepository
import vn.edu.usth.instaclone.data.repository.FakeLikeRepository
import vn.edu.usth.instaclone.data.repository.LikeRepository


object AppContainer {
    private val fakePostRepository by lazy { FakePostRepository() }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
    val chatRepository: ChatRepository by lazy { FakeChatRepository() }
    val postRepository: PostRepository get() = fakePostRepository
    val userRepository: UserRepository by lazy { FakeUserRepository() }
    val followRepository: FollowRepository by lazy { FakeFollowRepository() }
    val imageStorage: ImageStorage by lazy { FakeImageStorage() }
    val commentRepository: CommentRepository by lazy { FakeCommentRepository(fakePostRepository) }
    val likeRepository: LikeRepository by lazy { FakeLikeRepository() }
}