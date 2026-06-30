package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.data.OrderEntity
import com.example.multibarcode.data.Payment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val orders: List<OrderEntity> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val ordersTotal: Double = 0.0,
    val paymentsTotal: Double = 0.0,
) {
    val balance: Double get() = ordersTotal - paymentsTotal
}

class CustomerDetailViewModel(
    app: Application,
    private val customerId: Long,
) : AndroidViewModel(app) {

    private val repo = AppRepository.get(app)

    val state: StateFlow<CustomerDetailUiState> =
        combine(
            repo.observeCustomer(customerId),
            repo.ordersForCustomer(customerId),
            repo.paymentsForCustomer(customerId),
            repo.ordersTotal(customerId),
            repo.paymentsTotal(customerId),
        ) { customer, orders, payments, ordersTotal, paymentsTotal ->
            CustomerDetailUiState(
                customer = customer,
                orders = orders,
                payments = payments,
                ordersTotal = ordersTotal,
                paymentsTotal = paymentsTotal,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomerDetailUiState(),
        )

    fun addPayment(amount: Double, note: String?) {
        viewModelScope.launch {
            repo.addPayment(
                Payment(
                    customerId = customerId,
                    amount = amount,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch { repo.deletePayment(payment) }
    }
}
