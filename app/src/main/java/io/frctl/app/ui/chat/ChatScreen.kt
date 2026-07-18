package io.frctl.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.frctl.app.R
import io.frctl.app.ai.LocalAiViewModel
import io.frctl.app.data.LocalModelEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(model: LocalModelEntity, ai: LocalAiViewModel = viewModel(), back: () -> Unit) {
    val state by ai.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    val name = model.id.substringBefore("::").substringAfter('/')
    LaunchedEffect(model.id) { ai.startChat(model) }
    DisposableEffect(model.id) { onDispose { ai.leaveChat() } }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text(name); Text(stringResource(R.string.offline_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prompt, { prompt = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.chat_prompt)) }, enabled = !state.thinking, maxLines = 4)
                    if (state.thinking) FilledTonalIconButton(ai::stop) { Icon(Icons.Default.Stop, stringResource(R.string.stop_generation)) }
                    else FilledIconButton(onClick = { val value = prompt; prompt = ""; ai.send(model, value) }, enabled = prompt.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send_message)) }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.chat_private_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(state.messages, key = { index, _ -> index }) { _, message ->
                Card(colors = CardDefaults.cardColors(containerColor = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth(if (message.fromUser) 0.88f else 1f)) {
                    Text(message.text.ifBlank { stringResource(R.string.model_thinking) }, Modifier.padding(14.dp), fontWeight = if (message.fromUser) FontWeight.Medium else FontWeight.Normal)
                }
            }
            if (state.thinking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }
}
