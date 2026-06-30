package com.example.multibarcode.ui.order

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.OrderItem
import com.example.multibarcode.data.OrderRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrdersUiState(
    val page: Int = 0,
    val pageSize: Int = 15,
    val items: List<OrderRow> = emptyList(),
    val total: Int = 0,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

class OrdersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init { reload() }

    fun nextPage() {
        if (_state.value.canNext) { _state.value = _state.value.copy(page = _state.value.page + 1); reload() }
    }

    fun prevPage() {
        if (_state.value.canPrev) { _state.value = _state.value.copy(page = _state.value.page - 1); reload() }
    }

    fun reload() {
        val s = _state.value
        viewModelScope.launch {
            val total = repo.orderCount()
            val maxPage = if (total == 0) 0 else (total + s.pageSize - 1) / s.pageSize - 1
            val page = s.page.coerceIn(0, maxPage)
            val items = repo.orderPageRows(s.pageSize, page * s.pageSize)
            _state.value = _state.value.copy(items = items, total = total, page = page)
        }
    }

    suspend fun itemsOf(orderId: Long): List<OrderItem> = repo.orderItems(orderId)
}
