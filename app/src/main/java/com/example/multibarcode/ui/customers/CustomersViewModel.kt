package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.data.CustomerRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CustomerFilter(val key: String, val label: String) {
    ALL("ALL", "الكل"),
    DEBT("DEBT", "عليهم دين"),
    SETTLED("SETTLED", "مسدّدون"),
}

data class CustomersUiState(
    val query: String = "",
    val filter: CustomerFilter = CustomerFilter.ALL,
    val page: Int = 0,
    val pageSize: Int = 12,
    val items: List<CustomerRow> = emptyList(),
    val total: Int = 0,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

class CustomersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val pageSize = 12

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(CustomerFilter.ALL)
    private val pageFlow = MutableStateFlow(0)

    val state: StateFlow<CustomersUiState> =
        combine(
            repo.customersFlow(),
            repo.ordersFlow(),
            repo.paymentsFlow(),
            combine(queryFlow, filterFlow, pageFlow) { q, f, p -> Triple(q, f, p) },
        ) { customers, orders, payments, qfp ->
            val (q, filter, page) = qfp
            val ordersByCustomer = orders.groupBy { it.customerId }
            val paymentsByCustomer = payments.groupBy { it.customerId }

            val rows = customers.map { c ->
                val owed = ordersByCustomer[c.id].orEmpty().sumOf { it.total }
                val paid = paymentsByCustomer[c.id].orEmpty().sumOf { it.amount }
                CustomerRow(c.id, c.name, c.phone, c.note, c.createdAt, owed - paid)
            }
                .filter { q.isBlank() || it.name.contains(q, true) || (it.phone?.contains(q, true) == true) }
                .filter {
                    when (filter) {
                        CustomerFilter.ALL -> true
                        CustomerFilter.DEBT -> it.balance > 0.001
                        CustomerFilter.SETTLED -> it.balance <= 0.001
                    }
                }
                .sortedBy { it.name.lowercase() }

            val total = rows.size
            val maxPage = if (total == 0) 0 else (total + pageSize - 1) / pageSize - 1
            val clamped = page.coerceIn(0, maxPage)
            CustomersUiState(
                query = q, filter = filter, page = clamped, pageSize = pageSize,
                items = rows.drop(clamped * pageSize).take(pageSize), total = total,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersUiState())

    fun setQuery(q: String) { queryFlow.value = q; pageFlow.value = 0 }
    fun setFilter(f: CustomerFilter) { filterFlow.value = f; pageFlow.value = 0 }
    fun nextPage() { if (state.value.canNext) pageFlow.value = pageFlow.value + 1 }
    fun prevPage() { if (state.value.canPrev) pageFlow.value = pageFlow.value - 1 }

    fun save(existingId: String?, name: String, phone: String?, note: String?, createdAt: Long = 0) {
        viewModelScope.launch {
            if (existingId.isNullOrBlank()) {
                repo.upsertCustomer(
                    Customer(name = name, phone = phone, note = note, createdAt = System.currentTimeMillis())
                )
            } else {
                repo.upsertCustomer(
                    Customer(id = existingId, name = name, phone = phone, note = note, createdAt = createdAt)
                )
            }
        }
    }

    fun delete(row: CustomerRow) {
        viewModelScope.launch {
            repo.deleteCustomer(Customer(id = row.id, name = row.name, phone = row.phone, note = row.note))
        }
    }
}
