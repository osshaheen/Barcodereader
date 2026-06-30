package com.example.multibarcode.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductsUiState(
    val query: String = "",
    val page: Int = 0,
    val pageSize: Int = 15,
    val items: List<Product> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

class ProductsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    private val _state = MutableStateFlow(ProductsUiState())
    val state: StateFlow<ProductsUiState> = _state.asStateFlow()

    init { reload() }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q, page = 0)
        reload()
    }

    fun nextPage() {
        if (_state.value.canNext) {
            _state.value = _state.value.copy(page = _state.value.page + 1)
            reload()
        }
    }

    fun prevPage() {
        if (_state.value.canPrev) {
            _state.value = _state.value.copy(page = _state.value.page - 1)
            reload()
        }
    }

    fun reload() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val total = repo.productCount(s.query)
            val maxPage = if (total == 0) 0 else (total + s.pageSize - 1) / s.pageSize - 1
            val page = s.page.coerceIn(0, maxPage)
            val items = repo.productPage(s.query, s.pageSize, page * s.pageSize)
            _state.value = _state.value.copy(
                items = items, total = total, page = page, loading = false,
            )
        }
    }

    fun save(existing: Product?, barcode: String, name: String, price: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existing == null) {
                repo.upsertProduct(
                    Product(barcode = barcode, name = name, price = price, createdAt = now)
                )
            } else {
                repo.upsertProduct(
                    existing.copy(barcode = barcode, name = name, price = price)
                )
            }
            reload()
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch {
            repo.deleteProduct(product)
            reload()
        }
    }
}
