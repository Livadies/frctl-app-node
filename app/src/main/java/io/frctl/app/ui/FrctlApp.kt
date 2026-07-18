package io.frctl.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.frctl.app.MainViewModel
import io.frctl.app.R
import io.frctl.app.data.SearchState
import io.frctl.app.data.LocalModelEntity
import io.frctl.app.data.libraryKey
import io.frctl.app.ui.detail.DetailScreen
import io.frctl.app.ui.chat.ChatScreen
import io.frctl.app.ui.components.LocalSharedScopes
import io.frctl.app.ui.components.SharedScopes
import io.frctl.app.ui.home.CatalogList
import io.frctl.app.ui.home.HomeScreen
import io.frctl.app.ui.search.SearchScreen
import io.frctl.app.ui.settings.SettingsScreen
import io.frctl.app.ui.theme.FrctlTheme
import io.frctl.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.collect

@Composable
fun FrctlRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("frctl-ui", 0) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(preferences.getString("theme", ThemeMode.DYNAMIC.name)!!) }.getOrDefault(ThemeMode.DYNAMIC)) }
    FrctlTheme(themeMode) {
        FrctlApp(vm, themeMode) { value -> themeMode = value; preferences.edit().putString("theme", value.name).apply() }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FrctlApp(vm: MainViewModel, themeMode: ThemeMode, setThemeMode: (ThemeMode) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var chatModel by remember { mutableStateOf<LocalModelEntity?>(null) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(5 * 60 * 1000L); vm.loadHome(true) }
    }
    PredictiveBackHandler(screen != "home") { progress -> progress.collect(); screen = if (screen == "chat") "detail" else "home" }
    SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)).togetherWith(fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)) },
            label = "frctl-navigation",
        ) { target ->
            val visibilityScope = this
            CompositionLocalProvider(LocalSharedScopes provides SharedScopes(sharedScope, visibilityScope)) {
                when (target) {
                    "detail" -> state.selected?.let { selected ->
                        val key = libraryKey(selected)
                        DetailScreen(selected, key in state.favoriteIds, key in state.installedIds, { vm.toggleFavorite(selected) }, { vm.toggleInstalled(selected) }, { model -> chatModel = model; screen = "chat" }) { screen = "home" }
                    }
                    "chat" -> chatModel?.let { model -> ChatScreen(model) { screen = "detail" } }
                    "settings" -> SettingsScreen(themeMode, setThemeMode, state.personalizationEnabled, state.interactionCount, vm::setPersonalization, vm::clearPersonalization) { screen = "home" }
                    else -> MainShell(target, { screen = it }, state, vm)
                }
            }
        }
    }
}

@Composable
private fun MainShell(current: String, navigate: (String) -> Unit, state: SearchState, vm: MainViewModel) {
    Scaffold(bottomBar = {
        NavigationBar {
            listOf(
                Triple("home", Icons.Default.Home, R.string.nav_apps),
                Triple("trending", Icons.AutoMirrored.Filled.TrendingUp, R.string.nav_trending),
                Triple("search", Icons.Default.Search, R.string.nav_search),
                Triple("settings", Icons.Default.Settings, R.string.nav_settings),
            ).forEach { (route, icon, label) -> NavigationBarItem(selected = current == route, onClick = { navigate(route) }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) }) }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (current) {
                "trending" -> CatalogList(stringResource(R.string.trending), state.trending, state.loading, state.error) { vm.select(it); navigate("detail") }
                "search" -> SearchScreen(state, vm::query, vm::search) { vm.select(it); navigate("detail") }
                else -> HomeScreen(state, { vm.loadHome(true) }, vm::category) { vm.select(it); navigate("detail") }
            }
        }
    }
}
