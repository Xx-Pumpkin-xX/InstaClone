package vn.edu.usth.instaclone.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Message
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.AuthRepository
import vn.edu.usth.instaclone.data.repository.ChatRepository
import vn.edu.usth.instaclone.data.repository.UserRepository
import java.util.UUID

const val MAX_MESSAGE_LENGTH = 1_000
private const val PAGE_SIZE = 30L
private const val SEND_TIMEOUT_MS = 10_000L

enum class SendStatus { SENT, SENDING, FAILED }

data class ChatBubble(
    val key: String,
    val text: String,
    val isMine: Boolean,
    val status: SendStatus = SendStatus.SENT,
)

data class ChatUiState(
    val otherUsername: String = "",
    val bubbles: List<ChatBubble> = emptyList(), // oldest first
    val draft: String = "",
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val reachedStart: Boolean = false,
    val errorMessage: String? = null,
    val userNotFound: Boolean = false,
) {
    val draftLength: Int get() = draft.trim().length
    val canSend: Boolean get() = draftLength in 1..MAX_MESSAGE_LENGTH && !userNotFound
}

private data class PendingMessage(val localId: String, val text: String, val failed: Boolean = false)

/** S-10 Chat (T-30, FR-19, NFR-18). One instance per opened chat. */
class ChatViewModel(
    private val otherUserId: String,
    private val chatRepository: ChatRepository = AppContainer.chatRepository,
    private val authRepository: AuthRepository = AppContainer.authRepository,
    private val userRepository: UserRepository = AppContainer.userRepository,
) : ViewModel() {
    private val myId: String get() = authRepository.currentUserId.value ?: "me"
    private val conversationId = chatRepository.conversationId(myId, otherUserId)

    // Every message seen so far (live window + older pages), by id. Later versions win,
    // so a message whose server time arrives a moment later gets updated in place.
    private val known = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val pending = MutableStateFlow<List<PendingMessage>>(emptyList())
    private val base = MutableStateFlow(ChatUiState(otherUsername = otherUserId))

    val uiState: StateFlow<ChatUiState> = combine(base, known, pending) { state, messages, waiting ->
        val sent = messages.values
            .sortedWith(compareBy<Message, Timestamp?>(nullsLast()) { it.createdAt })
            .map { ChatBubble(key = it.id, text = it.text, isMine = it.senderId == myId) }
        val notYetSent = waiting.filterNot { it.localId in messages }.map {
            ChatBubble(
                key = it.localId,
                text = it.text,
                isMine = true,
                status = if (it.failed) SendStatus.FAILED else SendStatus.SENDING,
            )
        }
        state.copy(bubbles = sent + notYetSent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState(otherUsername = otherUserId))

    init {
        // Live messages: the newest PAGE_SIZE, updated in real time.
        viewModelScope.launch {
            chatRepository.messages(conversationId, PAGE_SIZE)
                .catch { base.update { s -> s.copy(isLoading = false, errorMessage = "Couldn't load messages.") } }
                .collect { latest ->
                    known.update { old -> old + latest.associateBy { it.id } }
                    base.update { s -> s.copy(isLoading = false) }
                }
        }
        // Username for the top bar, and UC-14 2b: "User not found" when the account no longer exists.
        viewModelScope.launch {
            runCatching { userRepository.getUser(otherUserId) }
                .onSuccess { user ->
                    if (user == null) {
                        base.update { s -> s.copy(isLoading = false, errorMessage = "User not found", userNotFound = true) }
                    } else if (user.username.isNotBlank()) {
                        base.update { s -> s.copy(otherUsername = user.username) }
                    }
                }
            // On a network error we keep the uid as a fallback title; sending still works.
        }
    }

    fun onDraftChange(value: String) = base.update { it.copy(draft = value) }

    fun send() {
        val state = uiState.value
        if (!state.canSend) return
        val text = state.draft.trim()
        val localId = UUID.randomUUID().toString()
        pending.update { it + PendingMessage(localId, text) }
        base.update { it.copy(draft = "") }
        deliver(localId, text)
    }

    fun retry(localId: String) {
        val message = pending.value.firstOrNull { it.localId == localId } ?: return
        pending.update { list -> list.map { if (it.localId == localId) it.copy(failed = false) else it } }
        deliver(localId, message.text)
    }

    private fun deliver(localId: String, text: String) {
        viewModelScope.launch {
            try {
                // Offline, Firestore would wait forever; after 10 s we show "Not sent" instead (UC-14 4a).
                withTimeout(SEND_TIMEOUT_MS) {
                    chatRepository.sendMessage(User(id = otherUserId, username = base.value.otherUsername), text, localId)
                }
                pending.update { list -> list.filterNot { it.localId == localId } }
            } catch (e: Exception) {
                pending.update { list -> list.map { if (it.localId == localId) it.copy(failed = true) else it } }
            }
        }
    }

    /** Called when the user scrolls near the oldest loaded message. */
    fun loadOlder() {
        val state = base.value
        if (state.isLoading || state.isLoadingOlder || state.reachedStart) return
        val oldest = known.value.values.filter { it.createdAt != null }.minByOrNull { it.createdAt!! } ?: return
        base.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            try {
                val page = chatRepository.olderMessages(conversationId, oldest, PAGE_SIZE)
                known.update { old -> old + page.associateBy { it.id } }
                base.update { it.copy(isLoadingOlder = false, reachedStart = page.size < PAGE_SIZE) }
            } catch (e: Exception) {
                base.update { it.copy(isLoadingOlder = false) }
            }
        }
    }
}
