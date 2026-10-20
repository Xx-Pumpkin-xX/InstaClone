package vn.edu.usth.instaclone.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.ChatRepository

data class ConversationRow(
    val otherUserId: String,
    val otherUsername: String,
    val preview: String,
    val time: Timestamp?,
)

data class ConversationListUiState(
    val isLoading: Boolean = true,
    val rows: List<ConversationRow> = emptyList(),
    val errorMessage: String? = null,
)

/** S-09 Conversation list (FR-18, UC-14 alternate flow 1a). */
class ConversationListViewModel(
    chatRepository: ChatRepository = AppContainer.chatRepository,
    private val authRepository: AuthRepository = AppContainer.authRepository,
) : ViewModel() {
    private val myId: String get() = authRepository.currentUserId.value ?: "me"

    val uiState: StateFlow<ConversationListUiState> = chatRepository.conversations()
        .map { list ->
            ConversationListUiState(
                isLoading = false,
                rows = list.map { c ->
                    val otherId = c.otherUserId(myId)
                    ConversationRow(
                        otherUserId = otherId,
                        otherUsername = c.otherUsername(myId).ifBlank { otherId },
                        preview = if (c.lastSenderId == myId) "You: ${c.lastMessageText}" else c.lastMessageText,
                        time = c.lastMessageAt,
                    )
                },
            )
        }
        .catch { emit(ConversationListUiState(isLoading = false, errorMessage = "Couldn't load your messages.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationListUiState())
}
