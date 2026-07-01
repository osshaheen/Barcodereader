package com.example.multibarcode.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    init {
        // Pull the shared data once after sign-in so the screens aren't empty.
        viewModelScope.launch { repo.ensureInitialSync() }
    }

    /** How many local (offline) items are waiting to be uploaded. */
    val pendingCount: StateFlow<Int> =
        repo.pendingCountFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** True when another device uploaded changes we haven't pulled yet. */
    val hasUpdates: StateFlow<Boolean> =
        repo.hasRemoteUpdatesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True while a manual pull is in progress. */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Pull the latest shared data from the database (the "read new additions" button). */
    fun pull() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            repo.refreshFromRemote()
            _syncing.value = false
        }
    }
}
