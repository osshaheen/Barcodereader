package com.example.multibarcode.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.ui.components.CameraPermission
import com.example.multibarcode.ui.components.CameraScanner
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: NewOrderViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showCustomerPicker by remember { mutableStateOf(false) }
    var editLine by remember { mutableStateOf<CartLine?>(null) }

    LaunchedSavedEvent(ui.savedOrderId) {
        vm.consumeSavedEvent()
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("طلبية جديدة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleTorch) {
                        Icon(
                            if (ui.torch) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "الإضاءة",
                        )
                    }
                    IconButton(onClick = vm::clearCart) {
                        Icon(Icons.Default.Delete, contentDescription = "تفريغ السلة")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraPermission {
                CameraScanner(
                    onScan = vm::onScan,
                    highlighted = ui.scannedValues,
                    torchEnabled = ui.torch,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Basket panel sits above the camera feed.
            CartPanel(
                ui = ui,
                onInc = vm::increment,
                onDec = vm::decrement,
                onEdit = { editLine = it },
                onDelete = vm::remove,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Save bar pinned to the bottom.
            Surface(
                color = Color(0xEE101814),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("الأصناف: ${ui.itemCount}", color = Color.White)
                        Text(
                            "الإجمالي: ${Format.money(ui.total)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = { showCustomerPicker = true },
                        enabled = ui.lines.isNotEmpty(),
                    ) {
                        Text("حفظ الطلبية")
                    }
                }
            }
        }
    }

    ui.awaitingProductFor?.let { barcode ->
        AddUnknownProductDialog(
            barcode = barcode,
            onConfirm = { name, price -> vm.confirmNewProduct(barcode, name, price) },
            onDismiss = vm::cancelNewProduct,
        )
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            onPick = { customerId ->
                vm.saveOrder(customerId, null)
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false },
        )
    }

    editLine?.let { line ->
        EditCartLineDialog(
            line = line,
            onSave = { qty, price ->
                vm.setPrice(line.barcode, price)
                vm.setQuantity(line.barcode, qty)
                editLine = null
            },
            onDismiss = { editLine = null },
        )
    }
}

@Composable
private fun CartPanel(
    ui: NewOrderUiState,
    onInc: (String) -> Unit,
    onDec: (String) -> Unit,
    onEdit: (CartLine) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.lines.isEmpty()) {
        Surface(color = Color(0xCC101814), modifier = modifier.fillMaxWidth()) {
            Text(
                "وجّه الكاميرا نحو الباركود لإضافة المنتجات…",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    Surface(color = Color(0xE6101814), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(
                "السلة (${ui.itemCount}) — ${Format.money(ui.total)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ui.lines, key = { it.barcode }) { line ->
                    CartRow(line, onInc, onDec, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun CartRow(
    line: CartLine,
    onInc: (String) -> Unit,
    onDec: (String) -> Unit,
    onEdit: (CartLine) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x22FFFFFF))
            .clickable { onEdit(line) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${Format.money(line.price)} × ${line.quantity} = ${Format.money(line.lineTotal)}",
                color = Color(0xFF9FE9C0),
            )
        }
        IconButton(onClick = { onDec(line.barcode) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = Color.White)
        }
        Text(line.quantity.toString(), color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = { onInc(line.barcode) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Add, contentDescription = "زيادة", tint = Color.White)
        }
        IconButton(onClick = { onDelete(line.barcode) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFFF6B6B))
        }
    }
}
