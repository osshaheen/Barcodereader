package com.example.multibarcode.ui.order

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.DetectedBarcode
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.NewOrderItem
import com.example.multibarcode.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One basket line. Identity is the barcode, so re-scanning the same code increments quantity. */
data class CartLine(
    val barcode: String,
    val productId: String?,
    val name: String,
    val price: Double,
    val quantity: Int,
) {
    val lineTotal: Double get() = price * quantity
}

data class NewOrderUiState(
    val lines: List<CartLine> = emptyList(),
    val awaitingProductFor: String? = null,   // unknown barcode waiting to be added as a product
    val torch: Boolean = false,
    val savedOrderId: String? = null,         // one-shot event: order persisted
) {
    val total: Double get() = lines.sumOf { it.lineTotal }
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val scannedValues: Set<String> get() = lines.map { it.barcode }.toSet()
}

class NewOrderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    private val _ui = MutableStateFlow(NewOrderUiState())
    val ui: StateFlow<NewOrderUiState> = _ui.asStateFlow()

    /** Called by the camera for every newly seen code. */
    fun onScan(code: DetectedBarcode) {
        if (_ui.value.awaitingProductFor != null) return
        viewModelScope.launch {
            val existing = repo.findProductByBarcode(code.value)
            if (existing != null) {
                addToCart(existing.id, existing.barcode, existing.name, existing.price)
            } else {
                _ui.update { it.copy(awaitingProductFor = code.value) }
            }
        }
    }

    private fun addToCart(productId: String?, barcode: String, name: String, price: Double) {
        _ui.update { s ->
            val idx = s.lines.indexOfFirst { it.barcode == barcode }
            val lines = if (idx >= 0) {
                s.lines.toMutableList().also { list ->
                    val l = list[idx]
                    list[idx] = l.copy(quantity = l.quantity + 1)
                }
            } else {
                s.lines + CartLine(barcode, productId, name, price, 1)
            }
            s.copy(lines = lines)
        }
    }

    /** User confirmed details for an unknown barcode: save it as a product, then add to cart. */
    fun confirmNewProduct(barcode: String, name: String, price: Double) {
        viewModelScope.launch {
            val id = repo.upsertProduct(
                Product(barcode = barcode, name = name, price = price, createdAt = System.currentTimeMillis())
            )
            addToCart(id, barcode, name, price)
            _ui.update { it.copy(awaitingProductFor = null) }
        }
    }

    fun cancelNewProduct() {
        _ui.update { it.copy(awaitingProductFor = null) }
    }

    fun increment(barcode: String) = changeQty(barcode, +1)
    fun decrement(barcode: String) = changeQty(barcode, -1)

    private fun changeQty(barcode: String, delta: Int) {
        _ui.update { s ->
            val lines = s.lines.mapNotNull { line ->
                if (line.barcode != barcode) line
                else {
                    val q = line.quantity + delta
                    if (q <= 0) null else line.copy(quantity = q)
                }
            }
            s.copy(lines = lines)
        }
    }

    fun setQuantity(barcode: String, quantity: Int) {
        if (quantity <= 0) { remove(barcode); return }
        _ui.update { s ->
            s.copy(lines = s.lines.map { if (it.barcode == barcode) it.copy(quantity = quantity) else it })
        }
    }

    fun setPrice(barcode: String, price: Double) {
        _ui.update { s ->
            s.copy(lines = s.lines.map { if (it.barcode == barcode) it.copy(price = price) else it })
        }
    }

    fun remove(barcode: String) {
        _ui.update { s -> s.copy(lines = s.lines.filterNot { it.barcode == barcode }) }
    }

    fun clearCart() {
        _ui.update { it.copy(lines = emptyList()) }
    }

    fun toggleTorch() {
        _ui.update { it.copy(torch = !it.torch) }
    }

    /** Persist the basket as an order tied to [customerId] (null = cash sale, no debt). */
    fun saveOrder(customerId: String?, note: String?) {
        val lines = _ui.value.lines
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val id = repo.saveOrder(
                customerId = customerId,
                note = note,
                items = lines.map {
                    NewOrderItem(it.productId, it.barcode, it.name, it.price, it.quantity)
                },
                now = System.currentTimeMillis(),
            )
            _ui.update { NewOrderUiState(savedOrderId = id) }
        }
    }

    fun consumeSavedEvent() {
        _ui.update { it.copy(savedOrderId = null) }
    }
}
