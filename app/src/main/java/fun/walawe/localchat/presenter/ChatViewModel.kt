package `fun`.walawe.localchat.presenter

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `fun`.walawe.inference.GemmaInference
import `fun`.walawe.inference.model.InferenceBenchmark
import `fun`.walawe.inference.model.InferenceState
import `fun`.walawe.localchat.model.ChatMessage
import `fun`.walawe.localchat.model.ChatRole
import `fun`.walawe.localchat.model.ChatUiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.copy

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gemmaInference: GemmaInference
) : ViewModel(){
    private val TAG = "ChatViewModel"
    val errorState = MutableStateFlow<String?>(null)

    private val safeViewModelScope: CoroutineScope
        get() = CoroutineScope(
            viewModelScope.coroutineContext +
                    CoroutineExceptionHandler { _, throwable ->
                        Log.e(TAG, "Error in ViewModel coroutine", throwable)
                        errorState.value = throwable.message
                    }
        )

    private val _uiState = MutableStateFlow(ChatUiState())
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _modelState = MutableStateFlow<InferenceState>(InferenceState.Idle).also { flow ->
        viewModelScope.launch {
            gemmaInference.state.collect { flow.emit(it) }
        }
    }
    val modelState: StateFlow<ChatUiState> = combine(_uiState, _modelState){ uiState, state ->
        val isProcessing = when(state) {
            is InferenceState.Generating -> true
            is InferenceState.LoadingModel -> true
            else -> false
        }
        uiState.copy(
            isProcessing = isProcessing,
            error = if (state is InferenceState.Error) state.message else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    init {
        viewModelScope.launch {
            gemmaInference.loadModel()
            _uiState.update { it.copy(isNewConversation = true) }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (_modelState.value !is InferenceState.Ready) {
            postError("Model is not ready yet")
            return
        }

        val imageUri = _uiState.value.selectedImageUri
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            text = message,
            timestamp = currentTime(),
            imageUri = imageUri,
        )

        val assistantId = UUID.randomUUID().toString()
        val assistantMessage = ChatMessage(
            id = assistantId,
            role = ChatRole.Assistant,
            text = "",
            timestamp = "",
            isStreaming = true,
        )

        _messages.update {  listOf(assistantMessage, userMessage) + it }
        _uiState.update { it.copy(isNewConversation = false) }

        safeViewModelScope.launch {
            gemmaInference.generateText(
                prompt = message,
                onTokenReceived = { partial ->
                    appendToAssistant(assistantId, partial)
                },
                onComplete = { benchmark ->
                    finishAssistantStream(assistantId, benchmark)
                    Log.d(TAG, "sendMessage: Generation complete with benchmark: $benchmark")
                },
            )
        }
    }

    fun startNewConversation(){
        if(_modelState.value !is InferenceState.Ready){
            postError("Model is not ready yet")
            return
        }
        safeViewModelScope.launch {
            _messages.value = emptyList()
            gemmaInference.resetConversation()
            _uiState.update { it.copy(isNewConversation = true, selectedImageUri = null) }
        }
    }

    private fun appendToAssistant(id: String, token: String) =
        updateAssistantMessage(id) { it.copy(
            text = it.text + token,
            isStreaming = true,
            benchmark = null
        ) }

    private fun finishAssistantStream(id: String, benchmark: InferenceBenchmark) =
        updateAssistantMessage(id) { it.copy(
            isStreaming = false,
            timestamp = currentTime(),
            benchmark = benchmark
        ) }

    private fun updateAssistantMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { messages ->
            val index = messages.indexOfFirst { it.id == messageId }
            if (index == -1) return@update messages
            messages.toMutableList().also { it[index] = transform(it[index]) }
        }
    }

    fun setSelectedImageUri(uri: String?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    private fun currentTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(System.currentTimeMillis())
    }

    private fun postError(message: String) {
        errorState.update { message }
    }

    fun clearError() {
        errorState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        gemmaInference.close()
    }
}