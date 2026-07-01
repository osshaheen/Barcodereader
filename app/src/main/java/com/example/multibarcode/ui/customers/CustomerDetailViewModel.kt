package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.data.Order
import com.example.multibarcode.data.Payment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val orders: List<Order> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val ordersTotal: Double = 0.0,
    val paymentsTotal: Double = 0.0,
) {
    val balance: Double get() = ordersTotal - paymentsTotal
}

class CustomerDetailViewModel(
    app: Application,
    private val customerId: String,
) : AndroidViewModel(app) {

    private val repo = AppRepository.get(app)

    val state: StateFlow<CustomerDetailUiState> =
        combine(
            repo.customersFlow(),
            repo.ordersFlow(),
            repo.paymentsFlow(),
        ) { customers, orders, payments ->
            val customer = customers.firstOrNull { it.id == customerId }
            val myOrders = orders.filter { it.customerId == customerId }.sortedByDescending { it.createdAt }
            val myPayments = payments.filter { it.customerId == customerId }.sortedByDescending { it.createdAt }
            CustomerDetailUiState(
                customer = customer,
                orders = myOrders,
                payments = myPayments,
                ordersTotal = myOrders.sumOf { it.total },
                paymentsTotal = myPayments.sumOf { it.amount },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomerDetailUiState(),
        )

    /** True while a backup/reset is running, to disable the buttons and show progress. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** One-shot user message (success/error) to show as a snackbar; cleared after shown. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    /** Save an Excel backup of this customer to the storage Drive. */
    fun backupNow() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = repo.backupCustomer(customerId).message
            _busy.value = false
        }
    }

    /** Backup then wipe all of this customer's orders/payments ("تصفير الحساب"). */
    fun resetAccount() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = repo.resetCustomer(customerId).message
            _busy.value = false
        }
    }

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
