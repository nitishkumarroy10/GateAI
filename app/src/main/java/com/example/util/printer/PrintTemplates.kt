package com.example.util.printer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrintTemplates {
    private fun formatTime(time: Long): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))
    }

    fun generateVisitorPass(
        passId: String,
        visitorName: String,
        host: String,
        time: Long
    ): ByteArray {
        return EscPosBuilder()
            .init()
            .alignCenter()
            .setBold(true)
            .textLine("DELHI HUB GATE PASS")
            .textLine("VISITOR ENTRY")
            .setBold(false)
            .divider()
            .alignLeft()
            .textLine("Pass ID: $passId")
            .textLine("Name   : $visitorName")
            .textLine("Host   : $host")
            .textLine("Time   : ${formatTime(time)}")
            .divider()
            .lineFeed(2)
            .alignCenter()
            .textLine("________________________")
            .textLine("Visitor Signature")
            .lineFeed(1)
            .textLine("________________________")
            .textLine("Security Officer")
            .feedPaper(4)
            .build()
    }

    fun generateMaterialPass(
        movementId: String,
        type: String, // "INWARD" or "OUTWARD"
        party: String,
        itemsCount: Int,
        time: Long
    ): ByteArray {
        return EscPosBuilder()
            .init()
            .alignCenter()
            .setBold(true)
            .textLine("DELHI HUB GATE PASS")
            .textLine("MATERIAL $type")
            .setBold(false)
            .divider()
            .alignLeft()
            .textLine("Mov ID : $movementId")
            .textLine("Party  : $party")
            .textLine("Items  : $itemsCount")
            .textLine("Time   : ${formatTime(time)}")
            .divider()
            .lineFeed(2)
            .alignCenter()
            .textLine("________________________")
            .textLine("Authorized By")
            .lineFeed(1)
            .textLine("________________________")
            .textLine("Security Officer")
            .feedPaper(4)
            .build()
    }
}
