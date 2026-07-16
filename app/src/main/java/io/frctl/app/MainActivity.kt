package io.frctl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.frctl.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Neon = Color(0xFF57F287)
private val Cyan = Color(0xFF49D6FF)
private val Void = Color(0xFF090B10)
private val Panel = Color(0xFF141820)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FrctlTheme { FrctlApp() } }
    }
}

@Composable private fun FrctlTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Neon, secondary = Cyan, background = Void, surface = Panel, surfaceVariant = Color(0xFF202632), onBackground = Color(0xFFF2F5FA)),
    content = content
)

@Composable fun FrctlApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("home") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60 * 1000L)
            vm.loadHome(true)
        }
    }
    BackHandler(screen != "home") { screen = "home" }
    when (screen) {
        "detail" -> state.selected?.let { DetailScreen(it) { screen = "home" } }
        "settings" -> SettingsScreen { screen = "home" }
        else -> MainShell(screen, { screen = it }, state, vm)
    }
}

@Composable private fun MainShell(current: String, navigate: (String) -> Unit, state: SearchState, vm: MainViewModel) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF10141B)) {
                listOf(
                    Triple("home", Icons.Default.Home, R.string.nav_apps),
                    Triple("trending", Icons.Default.TrendingUp, R.string.nav_trending),
                    Triple("search", Icons.Default.Search, R.string.nav_search),
                    Triple("settings", Icons.Default.Settings, R.string.nav_settings)
                ).forEach { (route, icon, label) ->
                    NavigationBarItem(selected = current == route, onClick = { navigate(route) }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (current) {
                "trending" -> CatalogList(stringResource(R.string.trending), state.trending, state.loading, state.error) { vm.select(it); navigate("detail") }
                "search" -> SearchScreen(state, vm::query, vm::search, { vm.select(it); navigate("detail") })
                else -> HomeScreen(state, { vm.loadHome(true) }, vm::category, { vm.select(it); navigate("detail") })
            }
        }
    }
}

@Composable private fun HomeScreen(state: SearchState, refresh: () -> Unit, chooseCategory: (MarketCategory) -> Unit, open: (AppEntry) -> Unit) {
    val allEntries = remember(state.featured, state.trending, state.models) {
        (state.featured + state.trending + state.models).distinctBy { "${it.kind}:${it.id}" }
    }
    val filtered = remember(allEntries, state.category) { allEntries.filter { MarketplaceClassifier.matches(it, state.category) }.sortedByDescending { it.updatedAt } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF17232A), Void))).padding(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("FRCTL", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Text(stringResource(R.string.subtitle), color = Neon) }
                        IconButton(refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    }
                    Text(stringResource(R.string.catalog_intro), modifier = Modifier.padding(top = 14.dp), color = Color(0xFFB9C3CF))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.live_updates)) }, leadingIcon = { Icon(Icons.Default.Bolt, null) })
                        if (state.cached) AssistChip(onClick = {}, label = { Text(stringResource(R.string.cached_catalog)) }, leadingIcon = { Icon(Icons.Default.OfflineBolt, null) })
                    }
                    if (state.lastUpdatedAt > 0) Text(stringResource(R.string.updated_at, formattedTime(state.lastUpdatedAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E9AA8))
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MarketCategory.entries, key = { it.name }) { category ->
                    FilterChip(selected = state.category == category, onClick = { chooseCategory(category) }, label = { Text(categoryLabel(category)) })
                }
            }
        }
        if (state.loading && state.featured.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { ErrorCard(it, refresh) } }
        if (state.category != MarketCategory.ALL) {
            item { SectionTitle("${categoryLabel(state.category)} · ${filtered.size}") }
            if (filtered.isEmpty() && !state.loading) item { EmptyCategory() }
            items(filtered, key = { "${it.kind}:${it.id}" }) { AppRow(it, open) }
        } else if (state.featured.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.featured)) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.featured.take(8), key = { it.id }) { FeaturedCard(it, open) } } }
        }
        if (state.category == MarketCategory.ALL && state.models.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.ai_models)) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.models.take(10), key = { it.id }) { ModelCard(it, open) } } }
        }
        if (state.category == MarketCategory.ALL && state.trending.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.recently_updated)) }
            items(state.trending.take(14), key = { it.id }) { AppRow(it, open) }
        }
    }
}

@Composable private fun ModelCard(app: AppEntry, open: (AppEntry) -> Unit) {
    Card(Modifier.width(280.dp).height(185.dp).clickable { open(app) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF131C27))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 58); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.owner, color = Cyan, style = MaterialTheme.typography.labelMedium) } }
            Text(app.pipelineTag.replace('-', ' '), maxLines = 2, color = Color(0xFFBBC4D0), modifier = Modifier.padding(top = 12.dp))
            Text("↓ ${compact(app.downloads)}  ·  ♥ ${compact(app.stars)}", color = Neon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun FeaturedCard(app: AppEntry, open: (AppEntry) -> Unit) {
    Card(Modifier.width(280.dp).height(185.dp).clickable { open(app) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 58); Spacer(Modifier.width(12.dp)); Column { Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.owner, color = Cyan, style = MaterialTheme.typography.labelMedium) } }
            Text(app.description, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFBBC4D0), modifier = Modifier.padding(top = 12.dp))
            Text("★ ${compact(app.stars)}", color = Neon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun CatalogList(title: String, apps: List<AppEntry>, loading: Boolean, error: String?, open: (AppEntry) -> Unit) {
    LazyColumn { item { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp)) }; if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }; error?.let { item { ErrorCard(it) {} } }; items(apps, key = { it.id }) { AppRow(it, open) } }
}

@Composable private fun AppRow(app: AppEntry, open: (AppEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { open(app) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 64); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFFAEB8C5)); Text((if (app.kind == EntryKind.AI_MODEL) "${app.source} · ↓ ${compact(app.downloads)}" else "${app.owner} · ★ ${compact(app.stars)}") + updatedSuffix(app.updatedAt), color = Cyan, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable private fun AppIcon(app: AppEntry, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF26303B)), contentAlignment = Alignment.Center) {
        if (app.kind == EntryKind.AI_MODEL) Icon(Icons.Default.Psychology, null, tint = Neon, modifier = Modifier.size((size / 2).dp))
        else Text(app.name.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.Black)
        if (app.iconUrl != null) AsyncImage(model = app.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable private fun SearchScreen(state: SearchState, query: (String) -> Unit, search: () -> Unit, open: (AppEntry) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp))
        OutlinedTextField(state.query, query, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search_field"), placeholder = { Text(stringResource(R.string.search_hint)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(search) { Icon(Icons.Default.ArrowForward, stringResource(R.string.search)) } }, singleLine = true)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        LazyColumn { items(state.apps, key = { it.id }) { AppRow(it, open) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DetailScreen(app: AppEntry, back: () -> Unit) {
    val context = LocalContext.current
    val isModel = app.kind == EntryKind.AI_MODEL
    Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(18.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 88); Spacer(Modifier.width(16.dp)); Column { Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(app.owner, color = Cyan); Text(if (isModel) "↓ ${compact(app.downloads)} · ♥ ${compact(app.stars)}" else "★ ${compact(app.stars)}") } } }
            item { Text(app.description, modifier = Modifier.padding(vertical = 18.dp), maxLines = 5) }
            item { Button(enabled = isModel || app.apkUrl != null, onClick = { val url = if (isModel) app.repoUrl else app.apkUrl; url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("install_button"), shape = RoundedCornerShape(16.dp)) { Icon(if (isModel) Icons.Default.Psychology else Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (isModel) stringResource(R.string.open_model) else if (app.apkUrl == null) stringResource(R.string.no_apk) else stringResource(R.string.install)) } }
            if (!isModel) item { OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.repoUrl))) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.open_github)) } }
            item { SectionTitle(stringResource(R.string.full_description)) }
            item { LightweightMarkdown(app.readme.ifBlank { app.description }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val auth = remember { GitHubDeviceAuth() }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }; var mode by remember { mutableStateOf(TokenMode.BEARER) }; var message by remember { mutableStateOf<String?>(null) }; var code by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { token = store.token.first(); mode = store.mode.first() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.account)) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(18.dp)) {
            item { Text(stringResource(R.string.github_account), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(stringResource(R.string.github_benefit), color = Color(0xFFB7C0CC), modifier = Modifier.padding(vertical = 8.dp)) }
            item { Button(onClick = {
                if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?description=FRCTL&scopes="))); message = context.getString(R.string.oauth_setup_needed) }
                else scope.launch { runCatching { val dc = auth.begin(BuildConfig.GITHUB_CLIENT_ID); code = dc.userCode; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))); val access = auth.awaitToken(BuildConfig.GITHUB_CLIENT_ID, dc); store.save(access, TokenMode.BEARER); token = access; code = null; context.getString(R.string.connected) }.onSuccess { message = it }.onFailure { message = it.message } }
            }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("github_sign_in"), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sign_in_github)) } }
            code?.let { item { Card(Modifier.fillMaxWidth().padding(top = 12.dp)) { Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.enter_code)); Text(it, style = MaterialTheme.typography.headlineMedium, color = Neon) } } } }
            message?.let { item { Text(it, color = Cyan, modifier = Modifier.padding(vertical = 10.dp)) } }
            item { HorizontalDivider(Modifier.padding(vertical = 18.dp)); Text(stringResource(R.string.token_alternative), fontWeight = FontWeight.Bold); OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("token_field"), label = { Text(stringResource(R.string.github_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
            item { TokenMode.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = item }) { RadioButton(mode == item, { mode = item }); Text(when(item) { TokenMode.BEARER -> "Bearer"; TokenMode.TOKEN -> "token"; TokenMode.RAW -> "RAW" }) } } }
            item { OutlinedButton({ scope.launch { store.save(token, mode); message = context.getString(R.string.saved) } }, Modifier.fillMaxWidth().testTag("save_token")) { Text(stringResource(R.string.save)) }; Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))
@Composable private fun ErrorCard(text: String, retry: () -> Unit) = Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF321E27))) { Column(Modifier.padding(16.dp)) { Text(text); TextButton(retry) { Text(stringResource(R.string.retry)) } } }
private fun compact(value: Int) = when { value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f); value >= 1_000 -> "%.1fK".format(value / 1_000f); else -> value.toString() }
private fun formattedTime(value: Long): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun updatedSuffix(value: String): String = runCatching { " · " + DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault("")

@Composable private fun categoryLabel(category: MarketCategory): String = stringResource(when (category) {
    MarketCategory.ALL -> R.string.category_all
    MarketCategory.ANDROID -> R.string.category_android
    MarketCategory.AI -> R.string.category_ai
    MarketCategory.SECURITY -> R.string.category_security
    MarketCategory.REMOTE_ACCESS -> R.string.category_remote
    MarketCategory.TOOLS -> R.string.category_tools
    MarketCategory.MEDIA -> R.string.category_media
})

@Composable private fun EmptyCategory() = Text(stringResource(R.string.empty_category), color = Color(0xFFAEB8C5), modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp))

@Composable private fun LightweightMarkdown(markdown: String) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { markdown.lineSequence().take(220).forEach { raw -> val line = raw.trim(); when { line.startsWith("# ") -> Text(line.drop(2), style = MaterialTheme.typography.headlineMedium, color = Neon); line.startsWith("## ") -> Text(line.drop(3), style = MaterialTheme.typography.titleLarge, color = Neon); line.startsWith("### ") -> Text(line.drop(4), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); line.startsWith("- ") || line.startsWith("* ") -> Text("• " + line.drop(2)); line.isNotBlank() -> Text(line.replace(Regex("[*_`]{1,3}"), "")) } } } }
