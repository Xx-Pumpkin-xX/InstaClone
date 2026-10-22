package vn.edu.usth.instaclone.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.usth.instaclone.data.repository.fake.FakeUserRepository
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun typingPrefix_showsResultAndOpensProfile() {
        val viewModel = SearchViewModel(
            userRepository = FakeUserRepository(myId = { "me" }, latencyMillis = 0),
            scope = scope,
            debounceMillis = 0L,
        )
        var clickedUserId: String? = null

        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                SearchScreen(
                    onUserClick = { clickedUserId = it },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("ph")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("phong").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Phong").assertIsDisplayed()
        composeRule.onNodeWithText("phong").performClick()

        assertEquals("user-phong", clickedUserId)
    }
}
