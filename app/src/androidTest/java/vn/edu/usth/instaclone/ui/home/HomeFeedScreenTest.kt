package vn.edu.usth.instaclone.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme

@RunWith(AndroidJUnit4::class)
class HomeFeedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchingToFollowing_showsTheFollowingEmptyMessage() {
        val viewModel = HomeFeedViewModel(EmptyFeedRepository(), NoopLikeRepository)

        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                HomeFeedScreen(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Following").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Follow people to see their posts here")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Follow people to see their posts here").assertIsDisplayed()
    }

    @Test
    fun allTabIsSelectedByDefault() {
        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                FeedModeTabs(mode = FeedMode.ALL, onModeSelected = {})
            }
        }

        composeRule.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun emptyContentAllModeSaysNoPostsYet() {
        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                FeedEmptyContent(mode = FeedMode.ALL, isOnline = true, hasLoaded = true)
            }
        }

        composeRule.onNodeWithText("No posts yet").assertIsDisplayed()
    }

    @Test
    fun emptyContentOfflineWinsOverTheFollowingMessage() {
        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                FeedEmptyContent(mode = FeedMode.FOLLOWING, isOnline = false, hasLoaded = true)
            }
        }

        composeRule.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun emptyContentFailedLoadOffersRetry() {
        composeRule.setContent {
            InstaCloneTheme(dynamicColor = false) {
                FeedEmptyContent(
                    mode = FeedMode.ALL,
                    isOnline = true,
                    hasLoaded = true,
                    loadFailed = true,
                )
            }
        }

        composeRule.onNodeWithText("Couldn't load feed").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    private class EmptyFeedRepository : PostRepository {
        override suspend fun feedPage(after: Post?, limit: Int) = emptyList<Post>()

        override suspend fun followingFeedPage(after: Post?, limit: Int) = emptyList<Post>()

        override suspend fun userPosts(uid: String) = emptyList<Post>()

        override suspend fun getPost(postId: String): Post? = null

        override suspend fun createPost(imageUrl: String, imagePath: String, caption: String) = "new"

        override suspend fun deletePost(postId: String) = Unit
    }

    private object NoopLikeRepository : LikeRepository {
        override suspend fun isLiked(postId: String) = false

        override suspend fun setLiked(postId: String, like: Boolean) = Unit
    }
}
