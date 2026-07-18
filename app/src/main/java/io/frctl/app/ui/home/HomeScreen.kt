package io.frctl.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.frctl.app.R
import io.frctl.app.data.*
import io.frctl.app.ui.components.*
import io.frctl.app.ui.theme.Cyan
import io.frctl.app.ui.theme.Neon
import io.frctl.app.ui.theme.Panel
import io.frctl.app.ui.theme.Void

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: SearchState, refresh: () -> Unit, chooseCategory: (MarketCategory) -> Unit, open: (AppEntry) -> Unit) {
    val allEntries = remember(state.libraryEntries, state.featured, state.trending, state.models) {
        (state.libraryEntries + state.featured + state.trending + state.models).distinctBy { "${it.kind}:${it.id}" }
    }
    val filtered = remember(allEntries, state.category) { allEntries.filter { MarketplaceClassifier.matches(it, state.category) }.sortedByDescending { it.updatedAt } }
    PullToRefreshBox(isRefreshing = state.loading && state.featured.isNotEmpty(), onRefresh = refresh) {
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
                        if (state.offline && state.cachedAt != null) Text(stringResource(R.string.offline_catalog_date, formattedDateTime(state.cachedAt)), color = Color(0xFFFFC46B), modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MarketCategory.entries, key = { it.name }) { category -> FilterChip(selected = state.category == category, onClick = { chooseCategory(category) }, label = { Text(CategoryLabel(category)) }) }
                }
            }
            if (state.loading && state.featured.isEmpty()) item { CatalogSkeleton() }
            state.error?.let { item { ErrorCard(it, refresh) } }
            if (state.category != MarketCategory.ALL) {
                item { SectionTitle("${CategoryLabel(state.category)} · ${filtered.size}") }
                if (filtered.isEmpty() && !state.loading) item { EmptyCategory() }
                items(filtered, key = { "filtered:${it.kind}:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) }
            } else if (state.favoriteIds.isNotEmpty()) {
                val favorites = allEntries.filter { libraryKey(it) in state.favoriteIds }
                if (favorites.isNotEmpty()) { item { SectionTitle(stringResource(R.string.favorites)) }; items(favorites, key = { "favorite:${it.kind}:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) } }
            }
            if (state.category == MarketCategory.ALL && state.featured.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.featured)) }
                item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.featured.take(8), key = { it.id }) { FeaturedCard(it, open) } } }
            }
            if (state.category == MarketCategory.ALL && state.installedIds.isNotEmpty()) {
                val installed = allEntries.filter { libraryKey(it) in state.installedIds }
                if (installed.isNotEmpty()) { item { SectionTitle(stringResource(R.string.installed_library)) }; items(installed, key = { "installed:${it.kind}:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) } }
            }
            if (state.category == MarketCategory.ALL && state.models.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.ai_models)) }
                item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.models.take(10), key = { it.id }) { ModelCard(it, open) } } }
            }
            if (state.category == MarketCategory.ALL && state.trending.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recently_updated)) }
                items(state.trending.take(14), key = { "trending:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun ModelCard(app: AppEntry, open: (AppEntry) -> Unit) {
    Card(Modifier.width(280.dp).height(185.dp).clickable { open(app) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF131C27))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 58); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.owner, color = Cyan, style = MaterialTheme.typography.labelMedium) } }
            Text(app.pipelineTag.replace('-', ' '), maxLines = 2, color = Color(0xFFBBC4D0), modifier = Modifier.padding(top = 12.dp))
            Text("↓ ${compact(app.downloads)}  ·  ♥ ${compact(app.stars)}", color = Neon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun FeaturedCard(app: AppEntry, open: (AppEntry) -> Unit) {
    Card(Modifier.width(280.dp).height(185.dp).clickable { open(app) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 58); Spacer(Modifier.width(12.dp)); Column { Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.owner, color = Cyan, style = MaterialTheme.typography.labelMedium) } }
            Text(app.description, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFBBC4D0), modifier = Modifier.padding(top = 12.dp))
            Text("★ ${compact(app.stars)}", color = Neon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CatalogList(title: String, apps: List<AppEntry>, loading: Boolean, error: String?, open: (AppEntry) -> Unit) {
    LazyColumn { item { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp)) }; if (loading && apps.isEmpty()) item { CatalogSkeleton() }; error?.let { item { ErrorCard(it) {} } }; items(apps, key = { "catalog:${it.kind}:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) } }
}
