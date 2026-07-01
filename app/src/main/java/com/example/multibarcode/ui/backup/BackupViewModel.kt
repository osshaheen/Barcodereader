package com.example.multibarcode.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.BackupRecord
import com.example.multibarcode.util.XlsxReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val query: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    /** The record currently opened for full-content viewing, if any. */
    val opened: BackupRecord? = null,
    val openedSheets: List<XlsxReader.Sheet>? = null,
    val loadingContent: Boolean = false,
)

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository.get(app)

    val backups: StateFlow<List<BackupRecord>> =
        repo.backupsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(BackupUiState())
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    fun onQuery(q: String) { _ui.value = _ui.value.copy(query = q) }
    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    /** Filter records by customer name / file name against the current query. */
    fun filtered(all: List<BackupRecord>): List<BackupRecord> {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return all
        return all.filter {
            (it.customerName ?: "").contains(q, ignoreCase = true) ||
                it.fileName.contains(q, ignoreCase = true)
        }
    }

    fun backupAll() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val r = repo.backupAll()
            _ui.value = _ui.value.copy(busy = false, message = r.message)
        }
    }

    fun open(record: BackupRecord) {
        _ui.value = _ui.value.copy(opened = record, openedSheets = null, loadingContent = true)
        viewModelScope.launch {
            val sheets = repo.readBackup(record)
            _ui.value = _ui.value.copy(
                openedSheets = sheets,
                loadingContent = false,
                message = if (sheets == null) "تعذّر فتح الملف. تأكد من الاتصال ومن أن الملف ما زال موجوداً على Drive." else _ui.value.message,
            )
        }
    }

    fun closeOpened() {
        _ui.value = _ui.value.copy(opened = null, openedSheets = null, loadingContent = false)
    }
}
