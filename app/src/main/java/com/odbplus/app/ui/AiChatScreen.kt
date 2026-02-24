package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.ai.AiChatUiState
import com.odbplus.app.ai.AiChatViewModel
import com.odbplus.app.ai.GoogleSignInResult
import com.odbplus.app.ai.data.AiProvider
import com.odbplus.app.ai.data.ChatMessage
import com.odbplus.app.ai.data.MessageRole
import com.odbplus.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatScreen(viewModel: AiChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Header
            ChatHeader(
                uiState = uiState,
                onProviderClick = { viewModel.showProviderSelector() },
                onSettingsClick = { viewModel.showApiKeyDialog() },
                onClearHistory = { viewModel.clearHistory() },
                onGoogleSignOut = { viewModel.googleSignOut() }
            )

            // Subtle divider using theme border color
            HorizontalDivider(
                color = DarkBorder,
                thickness = 1.dp
            )

            // Content
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    LoadingContent()
                } else if (!uiState.hasApiKey && !uiState.isGoogleSignedIn) {
                    ApiKeyRequiredContent(
                        provider = uiState.selectedProvider,
                        onSetupClick = { viewModel.showApiKeyDialog() },
                        onGoogleSignIn = {
                            coroutineScope.launch {
                                viewModel.googleSignIn(context)
                            }
                        },
                        isGoogleSignInConfigured = uiState.isGoogleSignInConfigured
                    )
                } else if (uiState.messages.isEmpty()) {
                    EmptyStateContent(
                        suggestedPrompts = uiState.suggestedPrompts,
                        onPromptClick = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                } else {
                    MessageList(
                        messages = uiState.messages,
                        isSending = uiState.isSending,
                        onRetryClick = { viewModel.retryLastMessage() }
                    )
                }
            }

            // Input bar
            if (uiState.hasApiKey || uiState.isGoogleSignedIn) {
                ChatInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    isSending = uiState.isSending
                )
            }
        }

        // Provider Selector Dialog
        if (uiState.showProviderSelector) {
            ProviderSelectorDialog(
                currentProvider = uiState.selectedProvider,
                isGoogleSignedIn = uiState.isGoogleSignedIn,
                googleEmail = uiState.googleUserEmail,
                onProviderSelected = { viewModel.selectProvider(it) },
                onDismiss = { viewModel.hideProviderSelector() }
            )
        }

        // API Key Dialog
        if (uiState.showApiKeyDialog) {
            ApiKeyDialog(
                provider = uiState.selectedProvider,
                onDismiss = { viewModel.hideApiKeyDialog() },
                onSave = { key -> viewModel.saveApiKey(key) },
                onClear = { viewModel.clearApiKey() },
                hasExistingKey = uiState.hasApiKey,
                isLoading = uiState.isLoading,
                error = uiState.apiKeyError ?: uiState.googleSignInError,
                isGoogleSignedIn = uiState.isGoogleSignedIn,
                googleEmail = uiState.googleUserEmail,
                onGoogleSignIn = {
                    coroutineScope.launch {
                        viewModel.googleSignIn(context)
                    }
                },
                onGoogleSignOut = { viewModel.googleSignOut() },
                isGoogleSignInConfigured = uiState.isGoogleSignInConfigured
            )
        }

        // Error Snackbar
        if (uiState.errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = Color.White)
                    }
                },
                containerColor = RedError,
                contentColor = Color.White
            ) {
                Text(uiState.errorMessage!!)
            }
        }
    }
}

@Composable
private fun ChatHeader(
    uiState: AiChatUiState,
    onProviderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearHistory: () -> Unit,
    onGoogleSignOut: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title and connection status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.width(10.dp))
                // Glowing connection dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isConnected) GreenSuccess else TextTertiary)
                )
            }

            // Menu button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change AI Provider") },
                        onClick = {
                            showMenu = false
                            onProviderClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("API Key Settings") },
                        onClick = {
                            showMenu = false
                            onSettingsClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    )
                    if (uiState.isGoogleSignedIn) {
                        DropdownMenuItem(
                            text = { Text("Sign out (${uiState.googleUserEmail?.take(20) ?: "Google"})") },
                            onClick = {
                                showMenu = false
                                onGoogleSignOut()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        )
                    }
                    if (uiState.messages.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Clear History") },
                            onClick = {
                                showMenu = false
                                onClearHistory()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Provider chip row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider chip with brand-colored background
            val isFree = uiState.selectedProvider == AiProvider.GEMINI ||
                    uiState.selectedProvider == AiProvider.GROQ
            Surface(
                modifier = Modifier.clickable(onClick = onProviderClick),
                shape = RoundedCornerShape(20.dp),
                color = if (isFree)
                    GreenSuccess.copy(alpha = 0.15f)
                else
                    CyanPrimary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.selectedProvider.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFree) GreenSuccess else CyanPrimary
                    )
                    if (isFree) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Google Sign-In status
            if (uiState.isGoogleSignedIn && uiState.selectedProvider == AiProvider.GEMINI) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GoogleBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = uiState.googleUserEmail?.take(15)?.plus("...") ?: "Signed in",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoogleBlue
                    )
                }
            }

            // VIN display when connected
            if (uiState.isConnected) {
                Spacer(Modifier.width(8.dp))
                if (uiState.isFetchingVehicleInfo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = CyanPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                } else if (uiState.currentVin != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurfaceVariant
                    ) {
                        Text(
                            text = "VIN: ${uiState.currentVin}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = CyanPrimary)
    }
}

@Composable
private fun ApiKeyRequiredContent(
    provider: AiProvider,
    onSetupClick: () -> Unit,
    onGoogleSignIn: () -> Unit,
    isGoogleSignInConfigured: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon in a glowing circle
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = CyanPrimary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Setup Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (provider == AiProvider.GEMINI)
                "Sign in with Google or enter an API key to start using AI diagnostics."
            else
                "Enter your ${provider.displayName} API key to start using the AI diagnostics assistant.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )
        Spacer(Modifier.height(28.dp))

        // Google Sign-In button for Gemini
        if (provider == AiProvider.GEMINI && isGoogleSignInConfigured) {
            androidx.compose.material3.Button(
                onClick = onGoogleSignIn,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(
                    text = "G",
                    color = GoogleBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Sign in with Google",
                    color = Color.DarkGray
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "or",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
            Spacer(Modifier.height(16.dp))
        }

        TextButton(onClick = onSetupClick) {
            Text("Enter API Key Manually", color = CyanPrimary)
        }
    }
}

@Composable
private fun EmptyStateContent(
    suggestedPrompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.3f))

        Text(
            text = "Auto Diagnostics Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask questions about your vehicle's health, error codes, or maintenance.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )

        Spacer(Modifier.height(32.dp))

        if (suggestedPrompts.isNotEmpty()) {
            Text(
                text = "TRY ASKING",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestedPrompts.forEach { prompt ->
                    SuggestedPromptChip(
                        text = prompt,
                        onClick = { onPromptClick(prompt) }
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.5f))
    }
}

@Composable
private fun SuggestedPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onRetryClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isSending) 1 else 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                onRetryClick = if (message.isError) onRetryClick else null
            )
        }

        // Loading indicator while sending
        if (isSending) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CyanPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetryClick: (() -> Unit)?
) {
    val isUser = message.role == MessageRole.USER

    // Bubble background: gradient for user, solid for assistant
    val bubbleColor = when {
        message.isError -> RedContainer
        isUser -> CyanContainer
        else -> DarkSurfaceVariant
    }
    val bubbleBorder = when {
        message.isError -> RedError.copy(alpha = 0.3f)
        isUser -> CyanPrimary.copy(alpha = 0.2f)
        else -> DarkBorder
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(
                    width = 1.dp,
                    color = bubbleBorder,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        message.isError -> RedOnContainer
                        isUser -> CyanOnContainer
                        else -> TextPrimary
                    }
                )

                // Retry button for errors
                if (message.isError && onRetryClick != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRetryClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = RedLight
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", color = RedLight)
                    }
                }
            }
        }

        // Timestamp
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = timeFormat.format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask about your vehicle...",
                        color = TextTertiary
                    )
                },
                enabled = !isSending,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = CyanPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onSend,
                enabled = !isSending && value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (!isSending && value.isNotBlank())
                            CyanPrimary
                        else
                            DarkSurfaceVariant
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank())
                            TextOnAccent
                        else
                            TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSelectorDialog(
    currentProvider: AiProvider,
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    onProviderSelected: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select AI Provider",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AiProvider.sortedByCost().forEach { provider ->
                    ProviderOptionCard(
                        provider = provider,
                        isSelected = provider == currentProvider,
                        isGoogleSignedIn = isGoogleSignedIn && provider == AiProvider.GEMINI,
                        googleEmail = if (provider == AiProvider.GEMINI) googleEmail else null,
                        onClick = { onProviderSelected(provider) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyanPrimary)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
private fun ProviderOptionCard(
    provider: AiProvider,
    isSelected: Boolean,
    isGoogleSignedIn: Boolean = false,
    googleEmail: String? = null,
    onClick: () -> Unit
) {
    val isFree = provider == AiProvider.GEMINI || provider == AiProvider.GROQ

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(
                    width = 1.dp,
                    color = CyanPrimary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                CyanContainer
            else
                DarkSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) CyanOnContainer else TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isGoogleSignedIn) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoogleBlue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Google",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleBlue
                            )
                        }
                    }
                    if (isFree) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GreenSuccess.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "FREE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) CyanOnContainer.copy(alpha = 0.8f) else TextSecondary
            )
            if (isGoogleSignedIn && googleEmail != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Signed in as: $googleEmail",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleBlue
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = provider.costInfo,
                style = MaterialTheme.typography.labelSmall,
                color = if (isFree) GreenSuccess else TextTertiary,
                fontWeight = if (isFree) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(
    provider: AiProvider,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    hasExistingKey: Boolean,
    isLoading: Boolean,
    error: String?,
    isGoogleSignedIn: Boolean = false,
    googleEmail: String? = null,
    onGoogleSignIn: () -> Unit = {},
    onGoogleSignOut: () -> Unit = {},
    isGoogleSignInConfigured: Boolean = false
) {
    var apiKey by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val isFree = provider == AiProvider.GEMINI || provider == AiProvider.GROQ
    val instructions = provider.getSetupInstructions()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${provider.displayName} Setup",
                    fontWeight = FontWeight.Bold
                )
                if (isFree) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GreenSuccess.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "FREE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }
        },
        text = {
            Column {
                // Google Sign-In section for Gemini
                if (provider == AiProvider.GEMINI && isGoogleSignInConfigured) {
                    if (isGoogleSignedIn) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = GoogleBlue.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Signed in with Google",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleBlue
                                    )
                                    if (googleEmail != null) {
                                        Text(
                                            text = googleEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                TextButton(onClick = onGoogleSignOut) {
                                    Text("Sign Out", color = RedError)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "You're all set! Gemini is ready to use.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GreenSuccess
                        )
                    } else {
                        Text(
                            text = "Sign in with Google for the easiest setup:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))

                        androidx.compose.material3.Button(
                            onClick = onGoogleSignIn,
                            enabled = !isLoading,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = GoogleBlue
                                )
                            } else {
                                Text(
                                    text = "G",
                                    color = GoogleBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Sign in with Google",
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Or enter an API key manually:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Text(
                        text = if (hasExistingKey)
                            "Your API key is saved. Enter a new key to update it."
                        else
                            "Enter your ${provider.displayName} API key to enable AI diagnostics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Don't show API key input if already signed in with Google
                if (!(provider == AiProvider.GEMINI && isGoogleSignedIn)) {
                    // Cost info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFree)
                                GreenSuccess.copy(alpha = 0.1f)
                            else
                                DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = provider.costInfo,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFree) GreenSuccess else TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key", color = TextSecondary) },
                        placeholder = { Text(provider.keyPlaceholder, color = TextTertiary) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = error != null,
                        supportingText = if (error != null) {
                            { Text(error, color = RedError) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = CyanPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CyanPrimary
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    // Instructions with clickable link
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkSurfaceHigh
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "How to get your API key:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))

                            instructions.forEachIndexed { index, instruction ->
                                Text(
                                    text = "${index + 1}. $instruction",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Clickable link
                            val linkText = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        color = CyanPrimary,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append("Open ${provider.setupUrl.removePrefix("https://")}")
                                }
                            }
                            Text(
                                text = linkText,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri(provider.setupUrl)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (provider == AiProvider.GEMINI && isGoogleSignedIn) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = CyanPrimary)
                }
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CyanPrimary
                )
            } else {
                TextButton(
                    onClick = { onSave(apiKey) },
                    enabled = apiKey.isNotBlank()
                ) {
                    Text(
                        "Save",
                        color = if (apiKey.isNotBlank()) CyanPrimary else TextTertiary
                    )
                }
            }
        },
        dismissButton = {
            if (!(provider == AiProvider.GEMINI && isGoogleSignedIn)) {
                Row {
                    if (hasExistingKey) {
                        TextButton(onClick = onClear) {
                            Text("Remove Key", color = RedError)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    )
}
