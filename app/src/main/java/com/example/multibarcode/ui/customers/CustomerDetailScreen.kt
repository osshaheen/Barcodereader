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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.multibarcode.data.Order
import com.example.multibarcode.data.Payment
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
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
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showPayment by remember { mutableStateOf(false) }
    var deletePayment by remember { mutableStateOf<Payment?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showBalanceDetails by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
            item { SummaryCard(state, onClick = { showBalanceDetails = true }) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.backupNow() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("نسخة احتياطية")
                    }
                    Button(
                        onClick = { confirmReset = true },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تصفير الحساب")
                    }
                }
            }

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

    if (confirmReset) {
        com.example.multibarcode.ui.components.ConfirmDialog(
            title = "تصفير الحساب",
            message = "سيتم أخذ نسخة احتياطية (Excel) ثم حذف جميع طلبيات ومدفوعات هذا الزبون نهائياً. هل تريد المتابعة؟",
            onConfirm = { vm.resetAccount(); confirmReset = false },
            onDismiss = { confirmReset = false },
        )
    }

    if (showBalanceDetails) {
        BalanceDetailsDialog(state = state, onDismiss = { showBalanceDetails = false })
    }
}

/** Status of a customer's balance, used for the colored مدين/دائن/مسدّد label. */
private enum class BalanceStatus { DEBTOR, CREDITOR, SETTLED }

private fun balanceStatus(balance: Double): BalanceStatus = when {
    balance > 0.001 -> BalanceStatus.DEBTOR     // الزبون مدين لنا
    balance < -0.001 -> BalanceStatus.CREDITOR  // له رصيد (دائن)
    else -> BalanceStatus.SETTLED
}

private val debtorColor = Color(0xFFD32F2F)
private val creditorColor = Color(0xFF2E7D32)

private fun balanceColor(status: BalanceStatus): Color = when (status) {
    BalanceStatus.DEBTOR -> debtorColor
    else -> creditorColor
}

private fun balanceTitle(status: BalanceStatus): String = when (status) {
    BalanceStatus.DEBTOR -> "مدين"
    BalanceStatus.CREDITOR -> "دائن"
    BalanceStatus.SETTLED -> "الرصيد مسدّد"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryCard(state: CustomerDetailUiState, onClick: () -> Unit) {
    val status = balanceStatus(state.balance)
    val color = balanceColor(status)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Big colored status line: red = مدين (owes us), green = دائن / مسدّد.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(balanceTitle(status), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                if (status != BalanceStatus.SETTLED) {
                    Text(
                        Format.money(kotlin.math.abs(state.balance)),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Text(
                "اضغط لعرض كل التفاصيل",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            SummaryRow("إجمالي الطلبيات", Format.money(state.ordersTotal))
            SummaryRow("إجمالي المدفوعات", Format.money(state.paymentsTotal))
        }
    }
}

@Composable
private fun BalanceDetailsDialog(state: CustomerDetailUiState, onDismiss: () -> Unit) {
    val status = balanceStatus(state.balance)
    val color = balanceColor(status)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تفاصيل حساب ${state.customer?.name ?: "الزبون"}") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الحالة", fontWeight = FontWeight.Bold)
                    Text(
                        if (status == BalanceStatus.SETTLED) balanceTitle(status)
                        else "${balanceTitle(status)}: ${Format.money(kotlin.math.abs(state.balance))}",
                        color = color, fontWeight = FontWeight.Bold,
                    )
                }
                SummaryRow("إجمالي الطلبيات", Format.money(state.ordersTotal))
                SummaryRow("إجمالي المدفوعات", Format.money(state.paymentsTotal))

                SectionTitle("الطلبيات (${state.orders.size})")
                if (state.orders.isEmpty()) {
                    Text("لا توجد طلبيات.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        state.orders.forEach { o ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Format.dateTime(o.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(Format.money(o.total), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                SectionTitle("المدفوعات (${state.payments.size})")
                if (state.payments.isEmpty()) {
                    Text("لا توجد مدفوعات.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        state.payments.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Format.dateTime(p.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(Format.money(p.amount), color = creditorColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
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
private fun OrderRow(order: Order) {
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
