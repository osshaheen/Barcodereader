package com.example.multibarcode.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductsUiState(
    val query: String = "",
    val page: Int = 0,
    val pageSize: Int = 15,
    val items: List<Product> = emptyList(),
    val total: Int = 0,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

class ProductsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val pageSize = 15

    private val queryFlow = MutableStateFlow("")
    private val pageFlow = MutableStateFlow(0)

    val state: StateFlow<ProductsUiState> =
        combine(repo.productsFlow(), queryFlow, pageFlow) { products, q, page ->
            val filtered = products
                .filter { q.isBlank() || it.name.contains(q, true) || it.barcode.contains(q, true) }
                .sortedBy { it.name.lowercase() }
            val total = filtered.size
            val maxPage = if (total == 0) 0 else (total + pageSize - 1) / pageSize - 1
            val clamped = page.coerceIn(0, maxPage)
            ProductsUiState(
                query = q,
                page = clamped,
                pageSize = pageSize,
                items = filtered.drop(clamped * pageSize).take(pageSize),
                total = total,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductsUiState())

    fun setQuery(q: String) { queryFlow.value = q; pageFlow.value = 0 }
    fun nextPage() { if (state.value.canNext) pageFlow.value = pageFlow.value + 1 }
    fun prevPage() { if (state.value.canPrev) pageFlow.value = pageFlow.value - 1 }

    fun save(existing: Product?, barcode: String, name: String, price: Double) {
        viewModelScope.launch {
            if (existing == null) {
                repo.upsertProduct(
                    Product(barcode = barcode, name = name, price = price, createdAt = System.currentTimeMillis())
                )
            } else {
                repo.upsertProduct(existing.copy(barcode = barcode, name = name, price = price))
            }
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch { repo.deleteProduct(product) }
    }
}
