package `fun`.walawe.localchat.model

data class ChatUiState(
    val isNewConversation: Boolean = true,
    val isProcessing: Boolean = false,
    val selectedImageUri: String? = null,
    val error: String? = null,
)