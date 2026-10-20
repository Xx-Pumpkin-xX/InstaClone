package vn.edu.usth.instaclone.ui.post

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import vn.edu.usth.instaclone.data.repository.FakeImageStorage
import vn.edu.usth.instaclone.data.repository.FakePostRepository

class NewPostViewModelTest {

    private lateinit var fakeImageStorage: FakeImageStorage
    private lateinit var fakePostRepository: FakePostRepository
    private lateinit var viewModel: NewPostViewModel

    @Before
    fun setUp() {
        fakeImageStorage = FakeImageStorage()
        fakePostRepository = FakePostRepository()
        viewModel = NewPostViewModel(fakeImageStorage, fakePostRepository)
    }

    @Test
    fun initialState_hasNoImage_emptyCaption_andIdleState() {
        assertNull(viewModel.selectedUri.value)
        assertEquals("", viewModel.caption.value)
        assertEquals(NewPostUiState.Idle, viewModel.uiState.value)
        assertFalse(viewModel.showDiscardDialog.value)
    }

    @Test
    fun onCaptionChanged_updatesCaptionState() {
        viewModel.onCaptionChanged("Test caption for post")
        assertEquals("Test caption for post", viewModel.caption.value)
    }

    @Test
    fun onImageSelected_updatesSelectedUri() {
        val mockUri = mock(Uri::class.java)
        viewModel.onImageSelected(mockUri)
        assertEquals(mockUri, viewModel.selectedUri.value)
    }

    @Test
    fun onBackPress_showsDiscardDialog_whenCaptionIsNotEmpty() {
        var navigatedBack = false
        viewModel.onCaptionChanged("Draft caption")

        viewModel.onBackPress { navigatedBack = true }

        assertTrue(viewModel.showDiscardDialog.value)
        assertFalse(navigatedBack)
    }

    @Test
    fun confirmDiscard_resetsDraft_andNavigatesBack() {
        var navigatedBack = false
        val mockUri = mock(Uri::class.java)
        viewModel.onImageSelected(mockUri)
        viewModel.onCaptionChanged("Draft caption")

        viewModel.onBackPress { navigatedBack = true }
        assertTrue(viewModel.showDiscardDialog.value)

        viewModel.confirmDiscard { navigatedBack = true }

        assertNull(viewModel.selectedUri.value)
        assertEquals("", viewModel.caption.value)
        assertFalse(viewModel.showDiscardDialog.value)
        assertTrue(navigatedBack)
    }
}
