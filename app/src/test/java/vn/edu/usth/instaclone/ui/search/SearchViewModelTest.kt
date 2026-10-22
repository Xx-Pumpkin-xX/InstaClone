package vn.edu.usth.instaclone.ui.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeUserRepository
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Test
    fun blankQuery_isIdleAndDoesNotSearch() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertTrue(repository.queries.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.notFound)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun whitespaceQuery_isIdleAndDoesNotSearch() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("   ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertTrue(repository.queries.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun searchWaitsForDebounce() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")

        advanceTimeBy(299)
        runCurrent()
        assertTrue(repository.queries.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("ph"), repository.queries)
        assertEquals(listOf(SearchViewModel.RESULT_LIMIT), repository.limits)
        assertEquals(listOf("phong"), viewModel.uiState.value.results.map { it.username })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun newQuery_clearsPreviousResultsImmediately() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(listOf("phong"), viewModel.uiState.value.results.map { it.username })

        viewModel.onQueryChange("phz")

        assertEquals("phz", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun rapidTyping_searchesOnlyForTheLatestQuery() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("p")
        advanceTimeBy(100)
        viewModel.onQueryChange("ph")

        advanceTimeBy(299)
        runCurrent()
        assertTrue(repository.queries.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("ph"), repository.queries)
    }

    @Test
    fun query_isTrimmedAndLowercased() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("  PH  ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(listOf("ph"), repository.queries)
        assertEquals("  PH  ", viewModel.uiState.value.query)
    }

    @Test
    fun uppercaseQuery_returnsTheSameResultsAsLowercase() = runTest {
        val repository = FakeUserRepository(myId = { "me" }, latencyMillis = 0)
        val lowercaseViewModel = searchViewModel(repository)

        lowercaseViewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        val lowercaseResults = lowercaseViewModel.uiState.value.results.map { it.username }

        val uppercaseViewModel = searchViewModel(repository)
        uppercaseViewModel.onQueryChange("PH")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(
            lowercaseResults,
            uppercaseViewModel.uiState.value.results.map { it.username },
        )
        assertFalse(uppercaseViewModel.uiState.value.notFound)
    }

    @Test
    fun noMatch_setsNotFound() = runTest {
        val viewModel = searchViewModel(
            FakeUserRepository(myId = { "me" }, latencyMillis = 0),
        )

        viewModel.onQueryChange("zz")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertTrue(state.notFound)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun results_areCappedAtTwenty() = runTest {
        val users = (1..25).map { number ->
            User(id = "user-$number", username = "user$number")
        }
        val repository = RecordingUserRepository(results = users)
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("user")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(20, viewModel.uiState.value.results.size)
        assertEquals(listOf(20), repository.limits)
    }

    @Test
    fun queryLongerThanThirtyCharacters_isCapped() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("a".repeat(35))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(30, viewModel.uiState.value.query.length)
        assertEquals(listOf("a".repeat(30)), repository.queries)
    }

    @Test
    fun clearQuery_cancelsPendingSearchAndResetsState() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")
        advanceTimeBy(100)
        viewModel.clearQuery()

        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertTrue(repository.queries.isEmpty())
        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
        assertNull(state.errorMessage)
    }

    @Test
    fun networkFailure_showsOfflineMessage() = runTest {
        val viewModel = searchViewModel(FailingUserRepository(IOException("offline")))

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(SearchViewModel.OFFLINE_MESSAGE, state.errorMessage)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
    }

    @Test
    fun otherFailure_showsGenericMessage() = runTest {
        val viewModel = searchViewModel(FailingUserRepository(IllegalStateException("boom")))

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(
            SearchViewModel.SEARCH_FAILED_MESSAGE,
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun loadingIndicator_isShownWhileTheRepositoryIsPending() = runTest {
        val repository = GatedUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)

        repository.gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("ph"), viewModel.uiState.value.results.map { it.username })
    }

    @Test
    fun newQuery_cancelsThePreviousSearchResult() = runTest {
        val repository = GatedUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(listOf("ph"), repository.queries)

        viewModel.onQueryChange("tr")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(listOf("ph", "tr"), repository.queries)

        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("tr"), viewModel.uiState.value.results.map { it.username })
        assertFalse(viewModel.uiState.value.notFound)
    }

    @Test
    fun retry_runsTheCurrentQueryAgain() = runTest {
        val repository = RecordingUserRepository()
        val viewModel = searchViewModel(repository)

        viewModel.onQueryChange("ph")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(listOf("ph"), repository.queries)

        viewModel.retry()
        runCurrent()

        assertEquals(listOf("ph", "ph"), repository.queries)
    }

    private fun TestScope.searchViewModel(
        repository: UserRepository,
        debounceMillis: Long = SearchViewModel.DEBOUNCE_MILLIS,
    ): SearchViewModel = SearchViewModel(
        userRepository = repository,
        scope = backgroundScope,
        debounceMillis = debounceMillis,
    )

    private class RecordingUserRepository(
        private val results: List<User> = listOf(
            User(id = "user-phong", username = "phong", displayName = "Phong"),
        ),
    ) : UserRepository {
        val queries = mutableListOf<String>()
        val limits = mutableListOf<Int>()

        override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> {
            queries += prefix
            limits += limit
            return results
        }

        override suspend fun claimUsername(uid: String, username: String) = Unit

        override suspend fun getUser(uid: String): User? = null

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) = Unit
    }

    private class FailingUserRepository(
        private val failure: Exception,
    ) : UserRepository {
        override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> = throw failure

        override suspend fun claimUsername(uid: String, username: String) = throw failure

        override suspend fun getUser(uid: String): User? = throw failure

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) =
            throw failure
    }

    private class GatedUserRepository : UserRepository {
        val queries = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> {
            queries += prefix
            gate.await()
            return listOf(User(id = "user-$prefix", username = prefix, displayName = prefix))
        }

        override suspend fun claimUsername(uid: String, username: String) = Unit

        override suspend fun getUser(uid: String): User? = null

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) = Unit
    }
}
