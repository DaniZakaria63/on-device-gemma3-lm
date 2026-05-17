package `fun`.walawe.localchat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import `fun`.walawe.localchat.presenter.ChatViewModel
import `fun`.walawe.localchat.ui.theme.LocalChatTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LocalChatTheme {

            }
        }
    }
}
