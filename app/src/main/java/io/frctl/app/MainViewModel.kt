package io.frctl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.frctl.app.data.AppEntry
import io.frctl.app.data.MarketplaceRepository
import io.frctl.app.data.MarketCategory
import io.frctl.app.data.SearchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarketplaceRepository(app)
    private val mutable = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutable
    private var searchJob: Job? = null

    init {
        loadHome()
        viewModelScope.launch {
            repository.library().collect { library ->
                mutable.update { it.copy(favoriteIds = library.favoriteIds, installedIds = library.installedIds, libraryEntries = library.savedEntries) }
            }
        }
        viewModelScope.launch {
            repository.searchHistory().collect { history -> mutable.update { it.copy(searchHistory = history) } }
        }
    }

    fun query(value: String) {
        mutable.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isNotBlank()) searchJob = viewModelScope.launch {
            delay(300)
            performSearch()
        }
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch() }
    }

    private suspend fun performSearch() {
        val query = mutable.value.query.trim()
        mutable.update { it.copy(loading = true, error = null) }
        runCatching { repository.search(query) }
            .onSuccess { apps -> mutable.update { it.copy(loading = false, apps = apps, error = if (apps.isEmpty()) "No APK releases found" else null) } }
            .onFailure { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Network route unavailable") } }
        if (query.isNotBlank()) repository.recordSearch(query)
    }
    fun loadHome(force: Boolean = false) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        runCatching {
            val featured = repository.discover(force)
            val trending = repository.trending(force)
            val models = repository.models(force)
            HomeResult(
                featured.entries,
                trending.entries,
                models.entries,
                featured.cached || trending.cached || models.cached,
                featured.offline || trending.offline || models.offline,
                listOfNotNull(featured.cachedAt, trending.cachedAt, models.cachedAt).minOrNull(),
            )
        }.onSuccess { result ->
            mutable.update { it.copy(loading = false, featured = result.featured, trending = result.trending, models = result.models, cached = result.cached, offline = result.offline, cachedAt = result.cachedAt, lastUpdatedAt = System.currentTimeMillis()) }
        }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Catalog unavailable") } }
    }
    fun select(app: AppEntry) = viewModelScope.launch {
        mutable.update { it.copy(selected = app) }
        runCatching { repository.details(app) }.onSuccess { full -> mutable.update { it.copy(selected = full) } }
    }
    fun category(value: MarketCategory) = mutable.update { it.copy(category = value) }
    fun toggleFavorite(app: AppEntry) = viewModelScope.launch { repository.toggleFavorite(app) }
    fun toggleInstalled(app: AppEntry) = viewModelScope.launch { repository.toggleInstalled(app) }

    private data class HomeResult(
        val featured: List<AppEntry>,
        val trending: List<AppEntry>,
        val models: List<AppEntry>,
        val cached: Boolean,
        val offline: Boolean,
        val cachedAt: Long?,
    )
}
