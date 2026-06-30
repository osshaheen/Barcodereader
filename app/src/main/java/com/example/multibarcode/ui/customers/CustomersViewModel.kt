package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.data.CustomerRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val loading: Boolean = false,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

class CustomersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    private val _state = MutableStateFlow(CustomersUiState())
    val state: StateFlow<CustomersUiState> = _state.asStateFlow()

    init { reload() }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q, page = 0)
        reload()
    }

    fun setFilter(f: CustomerFilter) {
        _state.value = _state.value.copy(filter = f, page = 0)
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
            val total = repo.customerCount(s.query, s.filter.key)
            val maxPage = if (total == 0) 0 else (total + s.pageSize - 1) / s.pageSize - 1
            val page = s.page.coerceIn(0, maxPage)
            val items = repo.customerPage(s.query, s.filter.key, s.pageSize, page * s.pageSize)
            _state.value = _state.value.copy(
                items = items, total = total, page = page, loading = false,
            )
        }
    }

    fun save(existingId: Long?, name: String, phone: String?, note: String?, createdAt: Long = 0) {
        viewModelScope.launch {
            if (existingId == null || existingId == 0L) {
                repo.upsertCustomer(
                    Customer(
                        name = name, phone = phone, note = note,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } else {
                repo.updateCustomer(
                    Customer(
                        id = existingId, name = name, phone = phone, note = note,
                        createdAt = createdAt,
                    )
                )
            }
            reload()
        }
    }

    fun delete(row: CustomerRow) {
        viewModelScope.launch {
            repo.deleteCustomer(
                Customer(id = row.id, name = row.name, phone = row.phone, note = row.note)
            )
            reload()
        }
    }
}
