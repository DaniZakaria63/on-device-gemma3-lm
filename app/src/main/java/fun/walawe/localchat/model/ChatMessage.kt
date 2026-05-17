package `fun`.walawe.localchat.model

import `fun`.walawe.inference.model.InferenceBenchmark

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: String,
    val imageUri: String? = null,
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val benchmark: InferenceBenchmark? = null,
)
enum class ChatRole {
    User,
    Assistant,
    System,
}