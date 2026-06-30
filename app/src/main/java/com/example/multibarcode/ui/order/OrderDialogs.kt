package com.example.multibarcode.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.Customer
import com.example.multibarcode.ui.products.trimNumber
import com.example.multibarcode.util.Format

/** Runs [onEvent] once when [savedOrderId] becomes non-null. */
@Composable
fun LaunchedSavedEvent(savedOrderId: Long?, onEvent: () -> Unit) {
    LaunchedEffect(savedOrderId) {
        if (savedOrderId != null) onEvent()
    }
}

@Composable
fun AddUnknownProductDialog(
    barcode: String,
    onConfirm: (name: String, price: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    val price = priceText.toDoubleOrNull()
    val valid = name.isNotBlank() && price != null && price >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("منتج جديد") },
        text = {
            Column {
                Text("باركود: $barcode", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المنتج") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("السعر") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim(), price ?: 0.0) }) {
                Text("إضافة")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("تجاهل") } },
    )
}

@Composable
fun EditCartLineDialog(
    line: CartLine,
    onSave: (quantity: Int, price: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by remember { mutableStateOf(line.quantity.toString()) }
    var priceText by remember { mutableStateOf(trimNumber(line.price)) }
    val qty = qtyText.toIntOrNull()
    val price = priceText.toDoubleOrNull()
    val valid = qty != null && qty > 0 && price != null && price >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("الكمية") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("السعر") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(qty ?: 1, price ?: 0.0) }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Pick which customer the order belongs to (or a cash sale with no debt). */
@Composable
fun CustomerPickerDialog(
    onPick: (customerId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val customers by repo.customersFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }

    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.phone?.contains(query, ignoreCase = true) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار الزبون") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "بدون زبون (بيع نقدي)",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 12.dp),
                )
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { customer ->
                        CustomerPickRow(customer) { onPick(customer.id) }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CustomerPickRow(customer: Customer, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Text(customer.name, fontWeight = FontWeight.Bold)
        customer.phone?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}
