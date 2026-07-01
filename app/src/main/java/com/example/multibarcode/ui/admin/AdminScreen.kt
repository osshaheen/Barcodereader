package com.example.multibarcode.ui.admin

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    vm: AdminViewModel = viewModel(),
) {
    val requests by vm.requests.collectAsStateWithLifecycle()
    val allowlist by vm.allowlist.collectAsStateWithLifecycle()
    var newEmail by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("إدارة الصلاحيات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("طلبات الدخول (${requests.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            if (requests.isEmpty()) {
                item { Text("لا توجد طلبات معلّقة.") }
            } else {
                items(requests, key = { it.email }) { req ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(req.email, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.approve(req.email) }) {
                                Icon(Icons.Default.Check, contentDescription = "قبول", tint = Color(0xFF2E7D32))
                            }
                            IconButton(onClick = { vm.deny(req.email) }) {
                                Icon(Icons.Default.Close, contentDescription = "رفض", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item {
                Text("المسموح لهم (${allowlist.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("إضافة بريد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newEmail.contains("@"),
                        onClick = { vm.add(newEmail); newEmail = "" },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("إضافة")
                    }
                }
            }
            if (allowlist.isEmpty()) {
                item { Text("القائمة فارغة.") }
            } else {
                items(allowlist, key = { it }) { email ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(email, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.remove(email) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
