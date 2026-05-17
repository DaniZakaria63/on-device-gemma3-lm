package `fun`.walawe.localchat.presenter

import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `fun`.walawe.inference.GemmaInference
import `fun`.walawe.inference.model.InferenceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gemmaInference: GemmaInference
) : ViewModel(){
    private val TAG = "ChatViewModel"
    private val _modelState = MutableStateFlow<InferenceState>(InferenceState.Idle).also { flow ->
        viewModelScope.launch {
            gemmaInference.state.collect { flow.emit(it) }
        }
    }
        .asStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InferenceState.Idle)

    val responseBuffer= StringBuilder()
    init {
        viewModelScope.launch {
            gemmaInference.loadModel()
        }
    }

    fun sendMessage(message: String) {
        responseBuffer.clear()
        viewModelScope.launch {
            gemmaInference.generateText(
                prompt = message,
                onTokenReceived = { partial ->
                    responseBuffer.append(partial)
                    Log.d(TAG, "sendMessage: ")
                },
                onComplete = { benchmark ->
                    Log.d(TAG, "sendMessage: Generation complete with benchmark: $benchmark")
                },
            )
        }
    }
}