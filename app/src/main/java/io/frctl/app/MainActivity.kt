package io.frctl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.frctl.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Neon = Color(0xFF00FF9C)
private val Void = Color(0xFF050607)
private val Panel = Color(0xFF111518)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FrctlTheme { FrctlApp() } }
    }
}

@Composable private fun FrctlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Neon, secondary = Color(0xFF00D9FF), background = Void, surface = Panel, onBackground = Color(0xFFE7FFF4)), content = content)
}

@Composable fun FrctlApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("search") }
    when (screen) {
        "settings" -> SettingsScreen { screen = "search" }
        "detail" -> state.selected?.let { DetailScreen(it) { screen = "search" } }
        else -> SearchScreen(state, vm::query, vm::search, { vm.select(it); screen = "detail" }, { screen = "settings" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SearchScreen(state: SearchState, onQuery: (String) -> Unit, onSearch: () -> Unit, onOpen: (AppEntry) -> Unit, onSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("FRCTL // OPEN NODE", fontWeight = FontWeight.Black) }, actions = { IconButton(onClick = onSettings, modifier = Modifier.semanticsTestTag("settings_button")) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("FRICTIONLESS APP ENGINE", color = Neon)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth().semanticsTestTag("search_field"), label = { Text(stringResource(R.string.search)) }, singleLine = true)
            Button(onSearch, Modifier.fillMaxWidth().padding(top = 8.dp).semanticsTestTag("search_button"), shape = CutCornerShape(8.dp)) { Text("SCAN GITHUB + HF") }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) { items(state.apps, key = { it.id }) { AppCard(it, onOpen) } }
        }
    }
}

@Composable private fun AppCard(app: AppEntry, onOpen: (AppEntry) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(app) }, shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)) { Column(Modifier.padding(14.dp)) { Text(app.name.uppercase(), fontWeight = FontWeight.Bold, color = Neon); Text("${app.owner} // ${app.source}", style = MaterialTheme.typography.labelSmall); Text(app.description, maxLines = 2) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DetailScreen(app: AppEntry, back: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)) {
            item { Text("ROUTE: ${app.source}", color = Neon); Text(app.description, Modifier.padding(vertical = 12.dp)); Button(enabled = app.apkUrl != null, onClick = { app.apkUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, modifier = Modifier.fillMaxWidth().semanticsTestTag("install_button"), shape = CutCornerShape(8.dp)) { Text(stringResource(R.string.install)) }; Spacer(Modifier.height(20.dp)); Text("README // ON-DEVICE", color = Neon, fontWeight = FontWeight.Bold) }
            item { LightweightMarkdown(app.readme.ifBlank { app.description }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TokenMode.BEARER) }
    LaunchedEffect(Unit) { token = store.token.first(); mode = store.mode.first() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("AUTHORIZATION HEADER", color = Neon)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().padding(vertical = 10.dp).semanticsTestTag("token_field"), label = { Text("GitHub token") }, visualTransformation = PasswordVisualTransformation())
            TokenMode.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = item }) { RadioButton(mode == item, { mode = item }); Text(when(item) { TokenMode.BEARER -> "Bearer <token>"; TokenMode.TOKEN -> "token <token>"; TokenMode.RAW -> "RAW / naked token" }) } }
            Button({ scope.launch { store.save(token, mode); back() } }, Modifier.fillMaxWidth().padding(top = 12.dp).semanticsTestTag("save_token"), shape = CutCornerShape(8.dp)) { Text("SAVE LOCALLY") }
            Text("Stored only in Android DataStore. Never synced by FRCTL.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private fun Modifier.semanticsTestTag(tag: String) = testTag(tag)

@Composable private fun LightweightMarkdown(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        markdown.lineSequence().take(180).forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineMedium, color = Neon)
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleLarge, color = Neon)
                line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Text("▸ " + line.drop(2))
                line.isNotBlank() -> Text(line.replace(Regex("[*_`]{1,3}"), ""))
            }
        }
    }
}
