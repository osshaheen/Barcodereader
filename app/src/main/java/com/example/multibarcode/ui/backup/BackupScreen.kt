package com.example.multibarcode.ui.backup

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.data.BackupRecord
import com.example.multibarcode.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    vm: BackupViewModel = viewModel(),
) {
    val all by vm.backups.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val opened = ui.opened
    if (opened != null) {
        BackupContent(
            record = opened,
            sheets = ui.openedSheets,
            loading = ui.loadingContent,
            onBack = { vm.closeOpened() },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("النسخ الاحتياطية") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Button(
                onClick = { vm.backupAll() },
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("نسخ احتياطي كامل الآن")
            }

            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::onQuery,
                label = { Text("بحث باسم الزبون") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            val list = vm.filtered(all)
            if (list.isEmpty()) {
                Text(
                    "لا توجد نسخ احتياطية بعد.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { rec -> BackupRow(rec, onOpen = { vm.open(rec) }) }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(rec: BackupRecord, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.customerName ?: rec.fileName, fontWeight = FontWeight.Bold)
                Text(rec.fileName, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${kindLabel(rec.kind)} — ${Format.dateTime(rec.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "reset" -> "نسخة تصفير"
    "daily" -> "نسخة يومية"
    else -> "نسخة يدوية"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupContent(
    record: BackupRecord,
    sheets: List<com.example.multibarcode.util.XlsxReader.Sheet>?,
    loading: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record.customerName ?: record.fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            sheets.isNullOrEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("تعذّر عرض محتوى الملف.") }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text(record.fileName, style = MaterialTheme.typography.bodySmall) }
                sheets.forEach { sheet ->
                    item {
                        Text(sheet.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    item { SheetTable(sheet) }
                }
            }
        }
    }
}

@Composable
private fun SheetTable(sheet: com.example.multibarcode.util.XlsxReader.Sheet) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        sheet.rows.forEachIndexed { r, row ->
            Row(Modifier.padding(vertical = 2.dp)) {
                row.forEach { cell ->
                    Text(
                        cell,
                        modifier = Modifier.width(120.dp).padding(horizontal = 4.dp),
                        fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (r == 0) Divider()
        }
    }
}
