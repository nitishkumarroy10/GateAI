package com.example.util.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothPrinterManager private constructor(private val context: Context) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    companion object {
        private const val TAG = "BluetoothPrinterManager"
        private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        @Volatile
        private var INSTANCE: BluetoothPrinterManager? = null

        fun getInstance(context: Context): BluetoothPrinterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothPrinterManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return bluetoothAdapter.bondedDevices.toList()
    }

    suspend fun connect(macAddress: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return@withContext false
        
        try {
            disconnect()
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to printer: ${e.message}")
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }

    suspend fun print(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (outputStream == null) return@withContext false
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to printer: ${e.message}")
            false
        }
    }
}

class EscPosBuilder {
    private val bytes = mutableListOf<Byte>()

    fun init(): EscPosBuilder {
        bytes.add(0x1B)
        bytes.add(0x40)
        return this
    }

    fun alignCenter(): EscPosBuilder {
        bytes.add(0x1B)
        bytes.add(0x61)
        bytes.add(0x01)
        return this
    }

    fun alignLeft(): EscPosBuilder {
        bytes.add(0x1B)
        bytes.add(0x61)
        bytes.add(0x00)
        return this
    }

    fun setBold(bold: Boolean): EscPosBuilder {
        bytes.add(0x1B)
        bytes.add(0x45)
        bytes.add(if (bold) 0x01 else 0x00)
        return this
    }

    fun text(text: String): EscPosBuilder {
        bytes.addAll(text.toByteArray(Charsets.US_ASCII).toList())
        return this
    }

    fun textLine(text: String): EscPosBuilder {
        text(text)
        lineFeed()
        return this
    }

    fun divider(): EscPosBuilder {
        textLine("--------------------------------")
        return this
    }

    fun lineFeed(lines: Int = 1): EscPosBuilder {
        for (i in 0 until lines) {
            bytes.add(0x0A)
        }
        return this
    }
    
    fun feedPaper(lines: Int = 4): EscPosBuilder {
        bytes.add(0x1B)
        bytes.add(0x64)
        bytes.add(lines.toByte())
        return this
    }

    fun build(): ByteArray {
        return bytes.toByteArray()
    }
}
