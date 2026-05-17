package `fun`.walawe.inference.model

sealed class InferenceState {
    object Idle : InferenceState()
    object LoadingModel : InferenceState()
    object Ready : InferenceState()
    object Generating : InferenceState()
    data class Error(val message: String, val cause: Throwable? = null) : InferenceState()
}