package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.util.printer.BluetoothPrinterManager
import com.example.util.printer.EscPosBuilder
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    
    val sharedPrefs = remember { context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE) }
    var savedMac by remember { mutableStateOf(sharedPrefs.getString("printer_mac", null)) }
    
    val devices = remember { printerManager.getPairedDevices() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Printer") },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text("No paired Bluetooth devices found. Please pair a printer in Android settings.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(devices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        savedMac = device.address
                                        sharedPrefs.edit().putString("printer_mac", device.address).apply()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = savedMac == device.address,
                                    onClick = null // Handled by Row click
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                                    Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (savedMac != null) {
                        coroutineScope.launch {
                            val connected = printerManager.connect(savedMac!!)
                            if (connected) {
                                val testBytes = EscPosBuilder()
                                    .init()
                                    .alignCenter()
                                    .setBold(true)
                                    .textLine("GATE AI")
                                    .textLine("TEST PRINT SUCCESS")
                                    .setBold(false)
                                    .feedPaper(3)
                                    .build()
                                val printed = printerManager.print(testBytes)
                                if (printed) {
                                    Toast.makeText(context, "Test print successful", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Print failed", Toast.LENGTH_SHORT).show()
                                }
                                printerManager.disconnect()
                            } else {
                                Toast.makeText(context, "Printer not connected", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Select a printer", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("TEST PRINT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}
