package io.frctl.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.frctl.app.R
import io.frctl.app.data.*
import io.frctl.app.ui.components.AppIcon
import io.frctl.app.ui.components.LightweightMarkdown
import io.frctl.app.ui.components.SectionTitle
import io.frctl.app.ui.components.compact
import io.frctl.app.ui.theme.Cyan
import io.frctl.app.ui.theme.Neon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(app: AppEntry, favorite: Boolean, installed: Boolean, toggleFavorite: () -> Unit, toggleInstalled: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isModel = app.kind == EntryKind.AI_MODEL
    var showApkWarning by remember { mutableStateOf(false) }
    fun open(url: String?) { url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    if (showApkWarning) {
        AlertDialog(
            onDismissRequest = { showApkWarning = false },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text(stringResource(R.string.apk_security_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.apk_security_warning)); Text(stringResource(R.string.apk_publisher, app.owner), fontWeight = FontWeight.Bold); Text(stringResource(R.string.apk_source, app.source))
                Text(stringResource(when (app.apkVerification) { ApkVerificationStatus.TRUSTED_CHECKSUM -> R.string.apk_status_trusted_checksum; ApkVerificationStatus.CHECKSUM_PUBLISHED -> R.string.apk_status_checksum; ApkVerificationStatus.TRUSTED_PUBLISHER -> R.string.apk_status_trusted_publisher; ApkVerificationStatus.UNVERIFIED -> R.string.apk_status_unverified }), color = if (app.apkVerification == ApkVerificationStatus.UNVERIFIED) Color(0xFFFFC46B) else Neon)
                app.apkSha256?.let { Text("SHA-256: $it", style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); showApkWarning = false; open(app.apkUrl) }) { Text(stringResource(R.string.continue_download)) } },
            dismissButton = { TextButton(onClick = { showApkWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(18.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(app, 88); Spacer(Modifier.width(16.dp)); Column { Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(app.owner, color = Cyan); Text(if (isModel) "↓ ${compact(app.downloads)} · ♥ ${compact(app.stars)}" else "★ ${compact(app.stars)}") } } }
            item { Text(app.description, modifier = Modifier.padding(vertical = 18.dp), maxLines = 5) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                FilterChip(selected = favorite, onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggleFavorite() }, label = { Text(stringResource(if (favorite) R.string.in_favorites else R.string.add_favorite)) }, leadingIcon = { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) })
                FilterChip(selected = installed, onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggleInstalled() }, label = { Text(stringResource(if (installed) R.string.marked_installed else R.string.mark_installed)) }, leadingIcon = { Icon(Icons.Default.Inventory2, null) })
            } }
            item { Button(enabled = isModel || app.apkUrl != null, onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); if (isModel) open(app.repoUrl) else showApkWarning = true }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("install_button"), shape = RoundedCornerShape(16.dp)) { Icon(if (isModel) Icons.Default.Psychology else Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (isModel) stringResource(R.string.open_model) else if (app.apkUrl == null) stringResource(R.string.no_apk) else stringResource(R.string.install)) } }
            if (!isModel) item { OutlinedButton(onClick = { open(app.repoUrl) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.open_github)) } }
            item { SectionTitle(stringResource(R.string.full_description)) }
            item { LightweightMarkdown(app.readme.ifBlank { app.description }) }
        }
    }
}
