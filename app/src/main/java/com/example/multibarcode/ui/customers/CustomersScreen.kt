package com.example.multibarcode.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.data.CustomerRow
import com.example.multibarcode.ui.components.ConfirmDialog
import com.example.multibarcode.ui.components.EmptyState
import com.example.multibarcode.ui.components.PagerBar
import com.example.multibarcode.ui.components.SearchField
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    vm: CustomersViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<CustomerRow?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CustomerRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الزبائن (${state.total})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة زبون")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = "ابحث بالاسم أو الهاتف",
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CustomerFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyState("لا يوجد زبائن. اضغط + للإضافة.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { row ->
                        CustomerCard(
                            row = row,
                            onOpen = { onOpenCustomer(row.id) },
                            onEdit = { editTarget = row; showEditor = true },
                            onDelete = { deleteTarget = row },
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
        CustomerEditDialog(
            initial = editTarget,
            onSave = { name, phone, note ->
                vm.save(editTarget?.id, name, phone, note, editTarget?.createdAt ?: 0L)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "حذف الزبون",
            message = "حذف \"${target.name}\"؟ لن تُحذف طلبياته السابقة.",
            onConfirm = { vm.delete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun CustomerCard(
    row: CustomerRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                row.phone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                BalanceLabel(row.balance)
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

@Composable
fun BalanceLabel(balance: Double) {
    val owed = balance > 0.001
    Text(
        text = if (owed) "مدين: ${Format.money(balance)}" else "الرصيد مسدّد",
        color = if (owed) Color(0xFFD32F2F) else Color(0xFF2E7D32),
        fontWeight = FontWeight.Bold,
    )
}
