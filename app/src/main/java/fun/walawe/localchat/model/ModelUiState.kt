package `fun`.walawe.localchat.model

import `fun`.walawe.inference.model.InferenceBenchmark

sealed class ModelUiState {
    object Loading : ModelUiState()
    object Ready : ModelUiState()
    data class Streaming(val partial: String) : ModelUiState()
    data class Done(val fullResponse: String, val benchmark: InferenceBenchmark) : ModelUiState()
    data class Error(val message: String) : ModelUiState()
}