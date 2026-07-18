package io.frctl.app.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.frctl.app.R
import io.frctl.app.data.AppEntry
import io.frctl.app.data.SearchState
import io.frctl.app.ui.components.AppRow

@Composable
fun SearchScreen(state: SearchState, query: (String) -> Unit, search: () -> Unit, open: (AppEntry) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp))
        OutlinedTextField(
            state.query,
            query,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search_field"),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(search) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.search)) } },
            singleLine = true,
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        if (state.query.isBlank() && state.apps.isEmpty()) {
            Text(stringResource(R.string.search_suggestions), color = Color(0xFFAEB8C5), modifier = Modifier.padding(18.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((state.searchHistory + listOf("remote desktop", "privacy", "text generation")).distinct().take(8)) { suggestion -> AssistChip(onClick = { query(suggestion) }, label = { Text(suggestion) }, leadingIcon = { Icon(Icons.Default.History, null) }) }
            }
        } else if (!state.loading && state.apps.isEmpty()) {
            Text(stringResource(R.string.search_empty), color = Color(0xFFAEB8C5), modifier = Modifier.padding(18.dp))
        }
        LazyColumn { items(state.apps, key = { "search:${it.kind}:${it.id}" }) { AppRow(it, open, Modifier.animateItem()) } }
    }
}
