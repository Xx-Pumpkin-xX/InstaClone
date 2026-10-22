package vn.edu.usth.instaclone.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.UserRepository
import java.io.IOException
import java.util.Locale

data class SearchUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * S-08 Search (FR-17, UC-13, US-13).
 *
 * Runs a prefix search 300 ms after typing stops. Every new query cancels the
 * previous search, so a slow result can never overwrite a newer one.
 */
class SearchViewModel(
    private val userRepository: UserRepository = AppContainer.userRepository,
    scope: CoroutineScope? = null,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val resultLimit: Int = RESULT_LIMIT,
) : ViewModel() {

    private val workScope: CoroutineScope = scope ?: viewModelScope
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var activeQuery: String = ""

    fun onQueryChange(rawQuery: String) {
        val clippedQuery = rawQuery.take(MAX_QUERY_LENGTH)
        val normalizedQuery = normalize(clippedQuery)
        val isSameQuery = normalizedQuery == activeQuery && normalizedQuery.isNotEmpty()

        _uiState.update { state ->
            state.copy(
                query = clippedQuery,
                results = if (normalizedQuery.isEmpty()) emptyList() else state.results,
                isLoading = if (isSameQuery) state.isLoading else false,
                notFound = if (isSameQuery) state.notFound else false,
                errorMessage = if (isSameQuery) state.errorMessage else null,
            )
        }

        if (!isSameQuery) {
            startSearch(normalizedQuery, delayMillis = debounceMillis)
        }
    }

    fun clearQuery() = onQueryChange("")

    fun retry() {
        val normalizedQuery = normalize(_uiState.value.query)
        if (normalizedQuery.isEmpty()) return
        startSearch(normalizedQuery, delayMillis = 0L)
    }

    private fun startSearch(normalizedQuery: String, delayMillis: Long) {
        searchJob?.cancel()
        activeQuery = normalizedQuery

        if (normalizedQuery.isEmpty()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    notFound = false,
                    errorMessage = null,
                )
            }
            return
        }

        searchJob = workScope.launch {
            try {
                if (delayMillis > 0L) delay(delayMillis)
                ensureActive()

                _uiState.update { it.copy(isLoading = true, notFound = false, errorMessage = null) }

                val results = withTimeout(SEARCH_TIMEOUT_MILLIS) {
                    userRepository.searchByPrefix(normalizedQuery, resultLimit)
                }.take(resultLimit.coerceAtLeast(0))

                ensureActive()
                if (activeQuery == normalizedQuery) {
                    _uiState.update {
                        it.copy(
                            results = results,
                            isLoading = false,
                            notFound = results.isEmpty(),
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (activeQuery == normalizedQuery) {
                    _uiState.update {
                        it.copy(
                            results = emptyList(),
                            isLoading = false,
                            notFound = false,
                            errorMessage = OFFLINE_MESSAGE,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeQuery == normalizedQuery) {
                    _uiState.update {
                        it.copy(
                            results = emptyList(),
                            isLoading = false,
                            notFound = false,
                            errorMessage = messageFor(e),
                        )
                    }
                }
            }
        }
    }

    private fun normalize(rawQuery: String): String =
        rawQuery.trim().lowercase(Locale.ROOT)

    private fun messageFor(e: Exception): String = when {
        e is FirebaseNetworkException -> OFFLINE_MESSAGE
        e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> OFFLINE_MESSAGE
        e is IOException -> OFFLINE_MESSAGE
        else -> SEARCH_FAILED_MESSAGE
    }

    companion object {
        const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
        const val SEARCH_FAILED_MESSAGE = "Couldn't search"
        const val MAX_QUERY_LENGTH = 30
        const val RESULT_LIMIT = 20
        const val DEBOUNCE_MILLIS = 300L
        private const val SEARCH_TIMEOUT_MILLIS = 10_000L
    }
}
