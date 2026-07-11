package io.frctl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.frctl.app.data.AppEntry
import io.frctl.app.data.MarketplaceRepository
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
            Triple(featured.first, trending.first, featured.second || trending.second)
        }.onSuccess { (featured, trending, cached) ->
            mutable.update { it.copy(loading = false, featured = featured, trending = trending, cached = cached) }
        }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Catalog unavailable") } }
    }
    fun select(app: AppEntry) = viewModelScope.launch {
        mutable.update { it.copy(selected = app) }
        runCatching { repository.details(app) }.onSuccess { full -> mutable.update { it.copy(selected = full) } }
    }
}
