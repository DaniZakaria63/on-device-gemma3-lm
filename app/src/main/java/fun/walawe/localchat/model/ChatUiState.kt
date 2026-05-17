package `fun`.walawe.localchat.model

import `fun`.walawe.inference.model.InferenceState

data class ChatUiState(
    val isNewConversation: Boolean = true,
    val isProcessing: Boolean = false,
    val selectedImageUri: String? = null,
    val error: String? = null,
    val modelState: InferenceState = InferenceState.Idle,
)