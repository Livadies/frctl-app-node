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
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarketplaceRepository(app)
    private val mutable = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutable

    init { loadHome() }

    fun query(value: String) = mutable.update { it.copy(query = value) }
    fun search() = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        runCatching { repository.search(mutable.value.query) }
            .onSuccess { apps -> mutable.update { it.copy(loading = false, apps = apps, error = if (apps.isEmpty()) "No APK releases found" else null) } }
            .onFailure { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Network route unavailable") } }
    }
    fun loadHome(force: Boolean = false) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        runCatching {
            val featured = repository.discover(force)
            val trending = repository.trending(force)
            val models = repository.models(force)
            HomeResult(featured.first, trending.first, models.first, featured.second || trending.second || models.second)
        }.onSuccess { result ->
            mutable.update { it.copy(loading = false, featured = result.featured, trending = result.trending, models = result.models, cached = result.cached, lastUpdatedAt = System.currentTimeMillis()) }
        }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Catalog unavailable") } }
    }
    fun select(app: AppEntry) = viewModelScope.launch {
        mutable.update { it.copy(selected = app) }
        runCatching { repository.details(app) }.onSuccess { full -> mutable.update { it.copy(selected = full) } }
    }
    fun category(value: MarketCategory) = mutable.update { it.copy(category = value) }

    private data class HomeResult(val featured: List<AppEntry>, val trending: List<AppEntry>, val models: List<AppEntry>, val cached: Boolean)
}
