package `fun`.walawe.localchat.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `fun`.walawe.inference.GemmaInference
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gemmaInference: GemmaInference
) : ViewModel(){

    init {
        viewModelScope.launch {
            gemmaInference.loadModel()
        }
    }
}