package io.frctl.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.frctl.app.BuildConfig
import io.frctl.app.R
import io.frctl.app.data.*
import io.frctl.app.ai.LocalModelManager
import io.frctl.app.ai.ModelPreferences
import io.frctl.app.ui.theme.Cyan
import io.frctl.app.ui.theme.Neon
import io.frctl.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class LanguageOption(val tag: String, val label: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeMode: ThemeMode, setThemeMode: (ThemeMode) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val auth = remember { GitHubDeviceAuth() }
    val modelManager = remember { LocalModelManager(context) }
    val modelPreferences = remember { ModelPreferences(context) }
    val localModelsFlow = remember { modelManager.localModels() }
    val localModels by localModelsFlow.collectAsState(initial = emptyList())
    val unmeteredOnly by modelPreferences.unmeteredOnly.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TokenMode.BEARER) }
    var message by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    val requiresReauth by store.requiresReauth.collectAsState(initial = false)
    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
    val languages = listOf(
        LanguageOption("", R.string.language_system),
        LanguageOption("ru", R.string.language_russian),
        LanguageOption("en", R.string.language_english),
        LanguageOption("zh", R.string.language_chinese),
        LanguageOption("de", R.string.language_german),
        LanguageOption("es", R.string.language_spanish),
    )
    LaunchedEffect(Unit) { token = store.token.first(); mode = store.mode.first() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.account)) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(18.dp)) {
            item { Text(stringResource(R.string.appearance_language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { Text(stringResource(R.string.theme_title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = themeMode == ThemeMode.DYNAMIC, onClick = { setThemeMode(ThemeMode.DYNAMIC) }, label = { Text(stringResource(R.string.theme_dynamic)) })
                FilterChip(selected = themeMode == ThemeMode.FRCTL, onClick = { setThemeMode(ThemeMode.FRCTL) }, label = { Text(stringResource(R.string.theme_frctl)) })
            } }
            item { Text(stringResource(R.string.language_title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)); languages.forEach { option -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag)) }) { RadioButton(selected = currentLanguage == option.tag || (currentLanguage.isBlank() && option.tag.isBlank()), onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag)) }); Text(stringResource(option.label)) } } }
            item { HorizontalDivider(Modifier.padding(vertical = 18.dp)); Text(stringResource(R.string.local_models), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(stringResource(R.string.models_storage, formatBytes(localModels.sumOf { it.sizeBytes })), color = Color(0xFFB7C0CC), modifier = Modifier.padding(top = 6.dp)) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.unmetered_only), Modifier.weight(1f)); Switch(unmeteredOnly, { value -> scope.launch { modelPreferences.setUnmeteredOnly(value) } }) } }
            if (localModels.isEmpty()) item { Text(stringResource(R.string.no_local_models), color = Color(0xFFB7C0CC), modifier = Modifier.padding(vertical = 8.dp)) }
            items(localModels, key = { it.id }) { model -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(model.id.substringBefore("::").substringAfter('/'), fontWeight = FontWeight.Bold); Text("${formatBytes(model.sizeBytes)} · ${model.status}", style = MaterialTheme.typography.bodySmall) }; IconButton({ scope.launch { modelManager.delete(model) } }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_model)) } } } }
            item { HorizontalDivider(Modifier.padding(vertical = 18.dp)); Text(stringResource(R.string.github_account), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(stringResource(R.string.github_benefit), color = Color(0xFFB7C0CC), modifier = Modifier.padding(vertical = 8.dp)) }
            if (requiresReauth) item { Text(stringResource(R.string.github_reauth_required), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp)) }
            item { Button(onClick = {
                if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?description=FRCTL&scopes="))); message = context.getString(R.string.oauth_setup_needed) }
                else scope.launch { runCatching { val dc = auth.begin(BuildConfig.GITHUB_CLIENT_ID); code = dc.userCode; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))); val access = auth.awaitToken(BuildConfig.GITHUB_CLIENT_ID, dc); store.save(access, TokenMode.BEARER); token = access; code = null; context.getString(R.string.connected) }.onSuccess { message = it }.onFailure { message = it.message } }
            }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("github_sign_in")) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sign_in_github)) } }
            code?.let { item { Card(Modifier.fillMaxWidth().padding(top = 12.dp)) { Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.enter_code)); Text(it, style = MaterialTheme.typography.headlineMedium, color = Neon) } } } }
            message?.let { item { Text(it, color = Cyan, modifier = Modifier.padding(vertical = 10.dp)) } }
            item { HorizontalDivider(Modifier.padding(vertical = 18.dp)); Text(stringResource(R.string.token_alternative), fontWeight = FontWeight.Bold); OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("token_field"), label = { Text(stringResource(R.string.github_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
            item { TokenMode.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = item }) { RadioButton(mode == item, { mode = item }); Text(when(item) { TokenMode.BEARER -> "Bearer"; TokenMode.TOKEN -> "token"; TokenMode.RAW -> "RAW" }) } } }
            item { OutlinedButton({ scope.launch { store.save(token, mode); message = context.getString(R.string.saved) } }, Modifier.fillMaxWidth().testTag("save_token")) { Text(stringResource(R.string.save)) }; Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.1f GB".format(value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
    else -> "%.1f KB".format(value / 1024.0)
}
