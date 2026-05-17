package `fun`.walawe.localchat.ui

/**
 * Created by Dani on 2026/6/11.
 * Copy from MemeChat by Dani on 2026/5/1.
 * Copyright (c) 2026 Dani. All rights reserved.
 */
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import `fun`.walawe.localchat.R
import `fun`.walawe.localchat.model.ChatMessage
import `fun`.walawe.localchat.model.ChatRole
import `fun`.walawe.localchat.model.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    messages: List<ChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit,
    onDismissError: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars
            .add(WindowInsets.navigationBars)
            .add(WindowInsets.displayCutout),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.fillMaxWidth().shadow(4.dp),
                windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
                title = {
                    Text(
                        text = if(uiState.isNewConversation) "New Conversation" else "Gemma 3n OnDevice Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(painterResource(R.drawable.ic_new_chat),
                            contentDescription = "New Chat",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            InputBar(
                inputText = inputText,
                selectedImageUri = uiState.selectedImageUri,
                onInputChange = onInputChange,
                onAttach = onAttach,
                onRemoveImage = onRemoveImage,
                onSend = onSend
            )
        }
    ){ paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).consumeWindowInsets(paddingValues)) {

            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            /*
            if (!uiState.error.isNullOrEmpty()) {
                TransientErrorDialog(
                    message = uiState.error,
                    onDismiss = onDismissError,
                )
            }*/
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage
) {
    val isUser = message.role == ChatRole.User
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    var collapseState by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        /*
        if(!isUser){
            CollapsibleReasoningSection(
                modifier = Modifier.padding(top =8.dp),
                title = Pair("Let me cook...", "Reasoning Process"),
                isStreaming = message.isStreaming,
                content = message.reasoning,
                isExpanded = collapseState,
                onToggle = { collapseState = !collapseState }
            )
        }
*/
        val modifier = if (isUser) {
            Modifier.padding(top = 8.dp)
        } else {
            Modifier.fillMaxWidth(0.9f).padding(top = 12.dp)
        }
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = if (isUser) CardDefaults.cardElevation(0.dp) else CardDefaults.cardElevation(1.dp),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                message.imageUri?.let {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(it)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        placeholder = painterResource(id = R.drawable.placeholder),
                    )
                }

                Text(
                    text = message.text.trim().ifEmpty { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.timestamp,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun InputBar(
    inputText: String,
    selectedImageUri: String?,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp, start = 16.dp, end = 16.dp) ,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(selectedImageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Selected image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(id = R.drawable.placeholder)
                    )
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove image")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                singleLine = false,
                maxLines = 4,
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Explain this meme",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModelButton(
                        onClick = { },
                        icon = Icons.Filled.AutoAwesome,
                        text = "Gemma 3n",
                        isHighlight = true
                    )

                    ModelButton(
                        onClick = onAttach,
                        icon = Icons.Filled.Image,
                        text = "Image",
                        isHighlight = false
                    )
                }

                FloatingActionButton(
                    onClick = onSend,
                    containerColor = if (inputText.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryFixed
                    } else MaterialTheme.colorScheme.secondaryFixedDim,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    isHighlight: Boolean
) {
    Surface(
        onClick = onClick,
        color = if (isHighlight) {
            MaterialTheme.colorScheme.surfaceVariant
        } else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (isHighlight)
            BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            ) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isHighlight) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                color = if (isHighlight) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}