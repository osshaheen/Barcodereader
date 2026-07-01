package com.example.multibarcode.ui.order

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.OrderItem
import com.example.multibarcode.data.OrderRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    private val pageSize = 15

    private val pageFlow = MutableStateFlow(0)

    // Keeps the latest orders so item details can be looked up without another query.
    private val itemsById = HashMap<String, List<OrderItem>>()

    val state: StateFlow<OrdersUiState> =
        combine(repo.ordersFlow(), repo.customersFlow(), pageFlow) { orders, customers, page ->
            val names = customers.associate { it.id to it.name }
            itemsById.clear()
            orders.forEach { itemsById[it.id] = it.items }

            val rows = orders
                .sortedByDescending { it.createdAt }
                .map { OrderRow(it.id, it.customerId?.let(names::get), it.total, it.itemCount, it.createdAt) }

            val total = rows.size
            val maxPage = if (total == 0) 0 else (total + pageSize - 1) / pageSize - 1
            val clamped = page.coerceIn(0, maxPage)
            OrdersUiState(
                page = clamped, pageSize = pageSize,
                items = rows.drop(clamped * pageSize).take(pageSize), total = total,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    fun nextPage() { if (state.value.canNext) pageFlow.value = pageFlow.value + 1 }
    fun prevPage() { if (state.value.canPrev) pageFlow.value = pageFlow.value - 1 }

    fun itemsOf(orderId: String): List<OrderItem> = itemsById[orderId].orEmpty()
}
