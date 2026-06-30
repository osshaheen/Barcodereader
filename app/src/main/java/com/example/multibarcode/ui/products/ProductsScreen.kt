package com.example.multibarcode.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.data.Product
import com.example.multibarcode.ui.components.ConfirmDialog
import com.example.multibarcode.ui.components.EmptyState
import com.example.multibarcode.ui.components.PagerBar
import com.example.multibarcode.ui.components.SearchField
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onBack: () -> Unit,
    vm: ProductsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<Product?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المنتجات (${state.total})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = "ابحث بالاسم أو الباركود",
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (state.items.isEmpty()) {
                EmptyState("لا توجد منتجات. اضغط + للإضافة.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            onEdit = { editTarget = product; showEditor = true },
                            onDelete = { deleteTarget = product },
                        )
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

    if (showEditor) {
        ProductEditDialog(
            initial = editTarget,
            onSave = { barcode, name, price ->
                vm.save(editTarget, barcode, name, price)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "حذف المنتج",
            message = "حذف \"${target.name}\"؟",
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ProductRow(product: Product, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(product.barcode, style = MaterialTheme.typography.bodySmall)
                Text(
                    Format.money(product.price),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
