package com.example.multibarcode.ui.products

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multibarcode.ui.components.CameraPermission
import com.example.multibarcode.ui.components.BulkCameraScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: BulkAddViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.savedCount) {
        if (ui.savedCount != null) {
            vm.consumeSaved()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مسح منتجات دفعة واحدة") },
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                CameraPermission {
                    BulkCameraScanner(
                        onCapture = vm::onCapture,
                        highlighted = ui.scannedValues,
                        torchEnabled = ui.torch,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                "مرّر الكاميرا على المنتجات — تم التقاط ${ui.items.size} منتج",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(12.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.items, key = { it.barcode }) { item ->
                    BulkRow(
                        item = item,
                        onName = { vm.setName(item.barcode, it) },
                        onPrice = { vm.setPrice(item.barcode, it) },
                        onDelete = { vm.remove(item.barcode) },
                    )
                }
            }

            ui.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }

            Button(
                onClick = vm::saveAll,
                enabled = ui.canSave,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                if (ui.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("حفظ الكل (${ui.items.size})")
                }
            }
        }
    }
}

@Composable
private fun BulkRow(
    item: BulkItem,
    onName: (String) -> Unit,
    onPrice: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val thumb = remember(item.barcode) {
        BitmapFactory.decodeByteArray(item.jpeg, 0, item.jpeg.size)?.asImageBitmap()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.barcode, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = item.name,
                onValueChange = onName,
                label = { Text("الاسم") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = item.priceText,
                onValueChange = onPrice,
                label = { Text("السعر") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
        }
    }
}
