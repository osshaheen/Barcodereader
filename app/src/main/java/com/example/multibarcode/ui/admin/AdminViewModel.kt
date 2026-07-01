package com.example.multibarcode.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AccessRequest
import com.example.multibarcode.data.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val requests: StateFlow<List<AccessRequest>> =
        repo.accessRequestsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allowlist: StateFlow<List<String>> =
        repo.allowlistFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(email: String) = viewModelScope.launch { repo.approveRequest(email) }
    fun deny(email: String) = viewModelScope.launch { repo.denyRequest(email) }
    fun add(email: String) = viewModelScope.launch { repo.addAllowed(email) }
    fun remove(email: String) = viewModelScope.launch { repo.removeAllowed(email) }
}
