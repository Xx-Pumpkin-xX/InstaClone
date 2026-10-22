package vn.edu.usth.instaclone.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.usth.instaclone.data.repository.ImageStorage
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeUserRepository

class EditProfileViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun loadsCurrentProfileWithoutAllowingUsernameChanges() {
        val viewModel = viewModel()
        val state = viewModel.uiState.value

        assertTrue(state.userLoaded)
        assertEquals("you", state.username)
        assertEquals("You", state.displayName)
        assertFalse(state.hasChanges)
    }

    @Test
    fun saveUpdatesDisplayNameAndBio() = runBlocking {
        val repository = RecordingUserRepository()
        val viewModel = viewModel(userRepository = repository)
        viewModel.onDisplayNameChanged("Nam Nguyen")
        viewModel.onBioChanged("Android developer")

        viewModel.save(null)
        awaitSave(viewModel)

        val updated = repository.getUser("me")
        assertEquals("Nam Nguyen", updated?.displayName)
        assertEquals("Android developer", updated?.bio)
        assertTrue(viewModel.uiState.value.saveComplete)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test
    fun avatarIsUploadedToOwnAvatarPathAndSavedToProfile() = runBlocking {
        val storage = RecordingImageStorage()
        val repository = RecordingUserRepository()
        val viewModel = viewModel(
            userRepository = repository,
            imageStorage = storage,
        )
        viewModel.onAvatarSelected("content://photos/avatar")

        viewModel.save(null)
        awaitSave(viewModel)

        assertEquals("images/avatars/me.jpg", storage.uploadedPath)
        assertEquals("https://storage.example/avatar.jpg", repository.getUser("me")?.avatarUrl)
        assertTrue(viewModel.uiState.value.saveComplete)
    }

    @Test
    fun tooLongFieldsCannotBeSaved() = runBlocking {
        val repository = RecordingUserRepository()
        val viewModel = viewModel(userRepository = repository)
        viewModel.onDisplayNameChanged("x".repeat(EditProfileViewModel.MAX_DISPLAY_NAME_LENGTH + 1))
        viewModel.onBioChanged("x".repeat(EditProfileViewModel.MAX_BIO_LENGTH + 1))

        viewModel.save(null)
        delay(20)

        assertEquals(0, repository.updateCalls)
        assertFalse(viewModel.uiState.value.saveComplete)
    }

    @Test
    fun uploadFailureKeepsSelectedAvatarAndTextForRetry() = runBlocking {
        val storage = RecordingImageStorage(failure = IllegalStateException("upload failed"))
        val viewModel = viewModel(imageStorage = storage)
        val uri = "content://photos/avatar"
        viewModel.onDisplayNameChanged("Nam")
        viewModel.onAvatarSelected(uri)

        viewModel.save(null)
        awaitSave(viewModel)

        assertEquals(EditProfileViewModel.UPLOAD_FAILED_MESSAGE, viewModel.uiState.value.errorMessage)
        assertEquals(uri, viewModel.uiState.value.selectedAvatarUri)
        assertEquals("Nam", viewModel.uiState.value.displayName)
        assertTrue(viewModel.uiState.value.hasChanges)
    }

    @Test
    fun profileSaveFailureKeepsEditsForRetry() = runBlocking {
        val repository = RecordingUserRepository(updateFailure = IllegalStateException("save failed"))
        val viewModel = viewModel(userRepository = repository)
        viewModel.onBioChanged("Updated bio")

        viewModel.save(null)
        awaitSave(viewModel)

        assertEquals(EditProfileViewModel.SAVE_FAILED_MESSAGE, viewModel.uiState.value.errorMessage)
        assertEquals("Updated bio", viewModel.uiState.value.bio)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.saveComplete)
    }

    private fun viewModel(
        userRepository: UserRepository = RecordingUserRepository(),
        imageStorage: ImageStorage = RecordingImageStorage(),
    ) = EditProfileViewModel(
        userRepository = userRepository,
        imageStorage = imageStorage,
        currentUserId = { "me" },
        compressImage = { _, _ -> byteArrayOf(1, 2, 3) },
        scope = scope,
    )

    private suspend fun awaitSave(viewModel: EditProfileViewModel) {
        withTimeout(5_000) {
            while (viewModel.uiState.value.isSaving) delay(10)
        }
    }

    private class RecordingUserRepository(
        private val delegate: FakeUserRepository = FakeUserRepository(
            myId = { "me" },
            latencyMillis = 0,
        ),
        private val updateFailure: Exception? = null,
    ) : UserRepository by delegate {
        var updateCalls = 0
            private set

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) {
            updateCalls++
            updateFailure?.let { throw it }
            delegate.updateProfile(displayName, bio, avatarUrl)
        }
    }

    private class RecordingImageStorage(
        private val failure: Exception? = null,
    ) : ImageStorage {
        var uploadedPath: String? = null
            private set

        override suspend fun upload(path: String, bytes: ByteArray): String {
            failure?.let { throw it }
            uploadedPath = path
            assertNotNull(bytes)
            return "https://storage.example/avatar.jpg"
        }

        override suspend fun delete(path: String) = Unit
    }
}
