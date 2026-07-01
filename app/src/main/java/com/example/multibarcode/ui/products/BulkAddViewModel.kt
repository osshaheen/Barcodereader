package com.example.multibarcode.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.DriveService
import com.example.multibarcode.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One captured product awaiting name/price before saving. */
data class BulkItem(
    val barcode: String,
    val jpeg: ByteArray,
    val name: String = "",
    val priceText: String = "",
) {
    val price: Double? get() = priceText.toDoubleOrNull()
    val valid: Boolean get() = name.isNotBlank() && (price?.let { it >= 0 } == true)
}

data class BulkUiState(
    val items: List<BulkItem> = emptyList(),
    val torch: Boolean = false,
    val saving: Boolean = false,
    val savedCount: Int? = null,
    val message: String? = null,
) {
    val scannedValues: Set<String> get() = items.map { it.barcode }.toSet()
    val canSave: Boolean get() = items.isNotEmpty() && items.all { it.valid } && !saving
}

class BulkAddViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    private val _ui = MutableStateFlow(BulkUiState())
    val ui: StateFlow<BulkUiState> = _ui.asStateFlow()

    /** New barcode + thumbnail captured by the camera. */
    fun onCapture(barcode: String, jpeg: ByteArray) {
        _ui.update { s ->
            if (s.items.any { it.barcode == barcode }) s
            else s.copy(items = s.items + BulkItem(barcode, jpeg))
        }
    }

    fun setName(barcode: String, name: String) = _ui.update { s ->
        s.copy(items = s.items.map { if (it.barcode == barcode) it.copy(name = name) else it })
    }

    fun setPrice(barcode: String, priceText: String) = _ui.update { s ->
        s.copy(items = s.items.map { if (it.barcode == barcode) it.copy(priceText = priceText) else it })
    }

    fun remove(barcode: String) = _ui.update { s ->
        s.copy(items = s.items.filterNot { it.barcode == barcode })
    }

    fun toggleTorch() = _ui.update { it.copy(torch = !it.torch) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** Upload each thumbnail to Drive, then save all products in one batch. */
    fun saveAll() {
        val items = _ui.value.items
        if (items.isEmpty() || items.any { !it.valid }) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, message = null) }
            val storageEmail = repo.getStorageDriveEmail()
            if (storageEmail.isNullOrBlank()) {
                _ui.update {
                    it.copy(saving = false, message = "لم يتم تعيين حساب Google Drive للصور. اضبطه من إدارة الصلاحيات.")
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val products = items.map { item ->
                val fileId = DriveService.uploadJpeg(context, storageEmail, "${item.name}-${item.barcode}.jpg", item.jpeg)
                Product(
                    barcode = item.barcode,
                    name = item.name.trim(),
                    price = item.price ?: 0.0,
                    createdAt = now,
                    imageFileId = fileId,
                )
            }
            try {
                repo.addProducts(products)
                val missing = products.count { it.imageFileId == null }
                _ui.update {
                    it.copy(
                        saving = false,
                        savedCount = products.size,
                        message = if (missing > 0) "تم الحفظ، لكن تعذّر رفع $missing صورة إلى Drive" else null,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, message = "تعذّر الحفظ: ${e.message}") }
            }
        }
    }

    fun consumeSaved() = _ui.update { it.copy(savedCount = null) }
}
