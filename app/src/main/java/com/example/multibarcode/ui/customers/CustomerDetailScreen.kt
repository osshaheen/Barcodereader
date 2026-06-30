package com.example.multibarcode.ui.customers

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.multibarcode.data.OrderEntity
import com.example.multibarcode.data.Payment
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: CustomerDetailViewModel = viewModel(
        key = "customer-$customerId",
        factory = viewModelFactory {
            initializer { CustomerDetailViewModel(app, customerId) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var showPayment by remember { mutableStateOf(false) }
    var deletePayment by remember { mutableStateOf<Payment?>(null) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(state.customer?.name ?: "زبون") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPayment = true },
                icon = { Icon(Icons.Default.Payments, contentDescription = null) },
                text = { Text("تسجيل دفعة") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SummaryCard(state) }

            state.customer?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                item { Text("الهاتف: $phone") }
            }
            state.customer?.note?.takeIf { it.isNotBlank() }?.let { note ->
                item { Text("ملاحظات: $note") }
            }

            item { SectionTitle("الطلبيات (${state.orders.size})") }
            if (state.orders.isEmpty()) {
                item { Text("لا توجد طلبيات.") }
            } else {
                items(state.orders, key = { "o${it.id}" }) { order -> OrderRow(order) }
            }

            item { SectionTitle("المدفوعات (${state.payments.size})") }
            if (state.payments.isEmpty()) {
                item { Text("لا توجد مدفوعات.") }
            } else {
                items(state.payments, key = { "p${it.id}" }) { payment ->
                    PaymentRow(payment, onDelete = { deletePayment = payment })
                }
            }

            item { Row(Modifier.padding(40.dp)) {} }
        }
    }

    if (showPayment) {
        PaymentDialog(
            onSave = { amount, note ->
                vm.addPayment(amount, note)
                showPayment = false
            },
            onDismiss = { showPayment = false },
        )
    }

    deletePayment?.let { payment ->
        com.example.multibarcode.ui.components.ConfirmDialog(
            title = "حذف الدفعة",
            message = "حذف دفعة بقيمة ${Format.money(payment.amount)}؟",
            onConfirm = { vm.deletePayment(payment); deletePayment = null },
            onDismiss = { deletePayment = null },
        )
    }
}

@Composable
private fun SummaryCard(state: CustomerDetailUiState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SummaryRow("إجمالي الطلبيات", Format.money(state.ordersTotal))
            SummaryRow("إجمالي المدفوعات", Format.money(state.paymentsTotal))
            BalanceLabel(state.balance)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun OrderRow(order: OrderEntity) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("طلبية #${order.id}", fontWeight = FontWeight.Bold)
                Text(Format.dateTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
                Text("${order.itemCount} صنف", style = MaterialTheme.typography.bodySmall)
            }
            Text(Format.money(order.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column {
                Text(Format.money(payment.amount), fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                Text(Format.dateTime(payment.createdAt), style = MaterialTheme.typography.bodySmall)
                payment.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    onSave: (amount: Double, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل دفعة") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(amount ?: 0.0, note.trim().ifBlank { null }) }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
