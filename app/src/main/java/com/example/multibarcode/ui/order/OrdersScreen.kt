package com.example.multibarcode.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.data.OrderItem
import com.example.multibarcode.data.OrderRow
import com.example.multibarcode.ui.components.EmptyState
import com.example.multibarcode.ui.components.PagerBar
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    vm: OrdersViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var detailOrder by remember { mutableStateOf<OrderRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الطلبيات (${state.total})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (state.items.isEmpty()) {
                EmptyState("لا توجد طلبيات بعد.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { order ->
                        OrderCard(order) { detailOrder = order }
                    }
                }
            }
            PagerBar(
                page = state.page,
                pageCount = state.pageCount,
                canPrev = state.canPrev,
                canNext = state.canNext,
                onPrev = vm::prevPage,
                onNext = vm::nextPage,
            )
        }
    }

    detailOrder?.let { order ->
        OrderItemsDialog(order = order, loadItems = { vm.itemsOf(order.id) }, onDismiss = { detailOrder = null })
    }
}

@Composable
private fun OrderCard(order: OrderRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("طلبية #${order.id}", fontWeight = FontWeight.Bold)
                Text(order.customerName ?: "بيع نقدي", style = MaterialTheme.typography.bodyMedium)
                Text(Format.dateTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
                Text("${order.itemCount} صنف", style = MaterialTheme.typography.bodySmall)
            }
            Text(Format.money(order.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OrderItemsDialog(
    order: OrderRow,
    loadItems: suspend () -> List<OrderItem>,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    LaunchedEffect(order.id) { items = loadItems() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طلبية #${order.id} — ${order.customerName ?: "بيع نقدي"}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(items, key = { it.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.padding(end = 8.dp)) {
                            Text(line.name, fontWeight = FontWeight.Bold)
                            Text("${Format.money(line.price)} × ${line.quantity}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Format.money(line.lineTotal), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
