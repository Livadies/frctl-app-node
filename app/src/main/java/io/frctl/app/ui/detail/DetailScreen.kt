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
import io.frctl.app.ai.*
import io.frctl.app.ui.components.AppIcon
import io.frctl.app.ui.components.LightweightMarkdown
import io.frctl.app.ui.components.SectionTitle
import io.frctl.app.ui.components.compact
import io.frctl.app.ui.theme.Cyan
import io.frctl.app.ui.theme.Neon
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(app: AppEntry, favorite: Boolean, installed: Boolean, toggleFavorite: () -> Unit, toggleInstalled: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isModel = app.kind == EntryKind.AI_MODEL
    val modelManager = remember { LocalModelManager(context) }
    val runnable = remember(app.id) { RunnableModelCatalog(context).find(app.id) }
    val localModelsFlow = remember { modelManager.localModels() }
    val localModels by localModelsFlow.collectAsState(initial = emptyList())
    val localModel = runnable?.let { target -> localModels.firstOrNull { it.id == target.key && it.status == LocalModelStatus.DOWNLOADED.name } }
    var eligibility by remember(runnable) { mutableStateOf<ModelEligibility?>(null) }
    var downloadState by remember(runnable) { mutableStateOf<ModelDownloadState?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(runnable) { eligibility = runnable?.let { modelManager.eligibility(it) } }
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
            item {
                val downloading = downloadJob?.isActive == true
                val modelAllowed = eligibility?.allowed == true
                Button(enabled = if (!isModel) app.apkUrl != null else runnable == null || localModel != null || modelAllowed || downloading, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when {
                        !isModel -> showApkWarning = true
                        runnable == null -> open(app.repoUrl)
                        downloading -> downloadJob?.cancel()
                        localModel == null -> downloadJob = scope.launch { modelManager.download(runnable).collect { downloadState = it } }
                    }
                }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("install_button"), shape = RoundedCornerShape(16.dp)) {
                    Icon(if (isModel) Icons.Default.Psychology else Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(when {
                        !isModel && app.apkUrl == null -> stringResource(R.string.no_apk)
                        !isModel -> stringResource(R.string.install)
                        runnable == null -> stringResource(R.string.open_model)
                        downloading -> stringResource(R.string.cancel_download)
                        localModel != null -> stringResource(R.string.model_ready)
                        else -> stringResource(R.string.run_on_device)
                    })
                }
                (downloadState as? ModelDownloadState.Progress)?.let { progress -> LinearProgressIndicator(progress = { (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                runnable?.let { Text(stringResource(R.string.model_requirements, formatModelSize(it.sizeBytes), formatModelSize(it.minRamBytes), it.license), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB7C0CC), modifier = Modifier.padding(top = 8.dp)) }
                if (runnable != null && localModel == null && eligibility?.allowed == false) Text(stringResource(when (eligibility?.reason) { ModelBlockReason.LOW_RAM -> R.string.model_low_ram; ModelBlockReason.LOW_STORAGE -> R.string.model_low_storage; ModelBlockReason.METERED_NETWORK -> R.string.model_metered; else -> R.string.model_unavailable }), color = Color(0xFFFFC46B), modifier = Modifier.padding(top = 6.dp))
                (downloadState as? ModelDownloadState.Error)?.let { Text(stringResource(R.string.model_download_failed, it.message), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (!isModel) item { OutlinedButton(onClick = { open(app.repoUrl) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.open_github)) } }
            item { SectionTitle(stringResource(R.string.full_description)) }
            item { LightweightMarkdown(app.readme.ifBlank { app.description }) }
        }
    }
}

private fun formatModelSize(value: Long): String = if (value >= 1024L * 1024L * 1024L) "%.1f GB".format(value / (1024.0 * 1024.0 * 1024.0)) else "%.0f MB".format(value / (1024.0 * 1024.0))
