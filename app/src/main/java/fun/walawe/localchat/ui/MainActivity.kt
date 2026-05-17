package `fun`.walawe.localchat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import `fun`.walawe.localchat.presenter.ChatViewModel
import `fun`.walawe.localchat.ui.theme.LocalChatTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            val uiState by viewModel.modelState.collectAsStateWithLifecycle()
            val messages by viewModel.messages.collectAsStateWithLifecycle()
            var inputText by remember { mutableStateOf("") }

            val galleryLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                    it?.let { uri ->
                        viewModel.setSelectedImageUri(uri.toString())
                    }
                }

            LocalChatTheme {
                ChatScreen(
                    uiState = uiState,
                    messages = messages,
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onNewChat = {
                        viewModel.startNewConversation()
                    },
                    onSend = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    onRemoveImage = {
                        viewModel.setSelectedImageUri(null)
                    },
                    onAttach = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onDismissError = {

                    }
                )
            }
        }
    }
}
