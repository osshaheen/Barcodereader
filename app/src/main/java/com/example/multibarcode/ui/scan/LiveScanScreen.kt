package com.example.multibarcode.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.multibarcode.ui.components.CameraPermission
import com.example.multibarcode.ui.components.CameraScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScanScreen(onBack: () -> Unit) {
    val scanned = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الماسح المباشر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraPermission {
                CameraScanner(
                    onScan = { code ->
                        scanned.remove(code.value)
                        scanned.add(0, code.value)
                        if (scanned.size > 50) scanned.removeAt(scanned.lastIndex)
                    },
                    highlighted = scanned.toSet(),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Surface(
                color = Color(0xE6101814),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        if (scanned.isEmpty()) "وجّه الكاميرا نحو عدة أكواد لقراءتها دفعةً واحدة"
                        else "تمت قراءة ${scanned.size} كود",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 8.dp)) {
                        items(scanned, key = { it }) { value ->
                            Text(value, color = Color(0xFF9FE9C0), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
