package com.example.multibarcode.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen scanner dialog that returns the first barcode it reads, then closes. */
@Composable
fun ScanBarcodeDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val handled = remember { BooleanHolder() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f), color = Color.Black) {
            Box(Modifier.fillMaxWidth()) {
                CameraPermission {
                    CameraScanner(
                        onScan = { code ->
                            if (!handled.value) {
                                handled.value = true
                                onResult(code.value)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text("إغلاق", color = Color.White)
                }
            }
        }
    }
}

private class BooleanHolder(var value: Boolean = false)
