package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.data.CustomerRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    val loading: Boolean = false,
) {
    val pageCount: Int get() = if (total == 0) 1 else (total + pageSize - 1) / pageSize
    val canPrev: Boolean get() = page > 0
    val canNext: Boolean get() = page < pageCount - 1
}

private data class Query(val q: String, val filter: CustomerFilter, val page: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val pageSize = 12

    private val queryFlow = MutableStateFlow(Query("", CustomerFilter.ALL, 0))

    /**
     * Fully reactive: the list re-emits whenever the customers, orders **or payments** tables
     * change, so a newly recorded payment immediately lowers the customer's balance here too.
     */
    val state: StateFlow<CustomersUiState> = queryFlow
        .flatMapLatest { qy ->
            repo.customerCountFlow(qy.q, qy.filter.key).flatMapLatest { total ->
                val maxPage = if (total == 0) 0 else (total + pageSize - 1) / pageSize - 1
                val page = qy.page.coerceIn(0, maxPage)
                repo.customerPageFlow(qy.q, qy.filter.key, pageSize, page * pageSize)
                    .map { items ->
                        CustomersUiState(
                            query = qy.q, filter = qy.filter, page = page,
                            pageSize = pageSize, items = items, total = total,
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomersUiState(),
        )

    fun setQuery(q: String) {
        queryFlow.value = queryFlow.value.copy(q = q, page = 0)
    }

    fun setFilter(f: CustomerFilter) {
        queryFlow.value = queryFlow.value.copy(filter = f, page = 0)
    }

    fun nextPage() {
        if (state.value.canNext) queryFlow.value = queryFlow.value.copy(page = queryFlow.value.page + 1)
    }

    fun prevPage() {
        if (state.value.canPrev) queryFlow.value = queryFlow.value.copy(page = queryFlow.value.page - 1)
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
        }
    }

    fun delete(row: CustomerRow) {
        viewModelScope.launch {
            repo.deleteCustomer(
                Customer(id = row.id, name = row.name, phone = row.phone, note = row.note)
            )
        }
    }
}
