package io.frctl.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.frctl.app.R
import io.frctl.app.data.*
import io.frctl.app.ui.theme.Cyan
import io.frctl.app.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalSharedTransitionApi::class)
data class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScopes = staticCompositionLocalOf<SharedScopes?> { null }

@Composable
fun AppRow(app: AppEntry, open: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { open(app) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 64)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(app.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFFAEB8C5))
            Text((if (app.kind == EntryKind.AI_MODEL) "${app.source} · ↓ ${compact(app.downloads)}" else "${app.owner} · ★ ${compact(app.stars)}") + updatedSuffix(app.updatedAt), color = Cyan, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppIcon(app: AppEntry, size: Int) {
    val scopes = LocalSharedScopes.current
    val sharedModifier = if (scopes == null) Modifier else with(scopes.transition) {
        Modifier.sharedElement(rememberSharedContentState("app-icon:${app.kind}:${app.id}"), scopes.visibility)
    }
    Box(sharedModifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF26303B)), contentAlignment = Alignment.Center) {
        if (app.kind == EntryKind.AI_MODEL) Icon(Icons.Default.Psychology, null, tint = Neon, modifier = Modifier.size((size / 2).dp))
        else Text(app.name.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.Black)
        app.iconUrl?.let { icon ->
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    app.fallbackIconUrl?.let { fallback ->
                        AsyncImage(model = fallback, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                },
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))

@Composable
fun ErrorCard(text: String, retry: () -> Unit) = Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF321E27))) {
    Column(Modifier.padding(16.dp)) { Text(text); TextButton(retry) { Text(stringResource(R.string.retry)) } }
}

@Composable
fun CategoryLabel(category: MarketCategory): String = stringResource(when (category) {
    MarketCategory.ALL -> R.string.category_all
    MarketCategory.ANDROID -> R.string.category_android
    MarketCategory.AI -> R.string.category_ai
    MarketCategory.SECURITY -> R.string.category_security
    MarketCategory.REMOTE_ACCESS -> R.string.category_remote
    MarketCategory.TOOLS -> R.string.category_tools
    MarketCategory.MEDIA -> R.string.category_media
})

@Composable
fun EmptyCategory() = Text(stringResource(R.string.empty_category), color = Color(0xFFAEB8C5), modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp))

@Composable
fun CatalogSkeleton() {
    val transition = rememberInfiniteTransition(label = "catalog-shimmer")
    val shift = transition.animateFloat(0f, 1200f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "shift")
    val brush = Brush.linearGradient(listOf(Color(0xFF171D25), Color(0xFF28323E), Color(0xFF171D25)), start = Offset(shift.value - 400f, 0f), end = Offset(shift.value, 400f))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(18.dp)) {
        repeat(5) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(brush)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.fillMaxWidth(.55f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(brush)); Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).background(brush)) } } }
    }
}

@Composable
fun LightweightMarkdown(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        markdown.lineSequence().take(220).forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("# ") -> Text(line.drop(2), style = MaterialTheme.typography.headlineMedium, color = Neon)
                line.startsWith("## ") -> Text(line.drop(3), style = MaterialTheme.typography.titleLarge, color = Neon)
                line.startsWith("### ") -> Text(line.drop(4), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Text("• " + line.drop(2))
                line.isNotBlank() -> Text(line.replace(Regex("[*_`]{1,3}"), ""))
            }
        }
    }
}

fun compact(value: Int) = when { value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f); value >= 1_000 -> "%.1fK".format(value / 1_000f); else -> value.toString() }
fun formattedTime(value: Long): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
fun formattedDateTime(value: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun updatedSuffix(value: String): String = runCatching { " · " + DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault("")
