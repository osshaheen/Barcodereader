package com.example.multibarcode.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.PendingOp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UploadUiState(
    val uploading: Boolean = false,
    val message: String? = null,
)

class UploadViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val pending: StateFlow<List<PendingOp>> =
        repo.pendingOpsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(UploadUiState())
    val ui: StateFlow<UploadUiState> = _ui.asStateFlow()

    fun upload() {
        viewModelScope.launch {
            _ui.value = UploadUiState(uploading = true)
            val uploaded = repo.uploadPending()
            _ui.value = UploadUiState(
                message = if (uploaded > 0) "تم رفع $uploaded عنصر" else "تعذّر الرفع — تحقّق من الإنترنت",
            )
        }
    }

    fun clearMessage() {
        if (_ui.value.message != null) _ui.value = _ui.value.copy(message = null)
    }
}
