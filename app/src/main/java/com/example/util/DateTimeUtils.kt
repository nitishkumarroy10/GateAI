package com.example.util

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {
    private const val PATTERN_DATETIME = "dd MMM yyyy, hh:mm a"
    private const val PATTERN_DATE = "dd MMM yyyy"
    private const val PATTERN_TIME = "hh:mm a"

    fun formatStandardDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat(PATTERN_DATETIME, Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    fun formatStandardDate(timestamp: Long): String {
        val sdf = SimpleDateFormat(PATTERN_DATE, Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    fun formatStandardTime(timestamp: Long): String {
        val sdf = SimpleDateFormat(PATTERN_TIME, Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    fun formatDuration(startMillis: Long, endMillis: Long = System.currentTimeMillis()): String {
        val diff = (endMillis - startMillis).coerceAtLeast(0L)
        val minutes = (diff / (1000 * 60)) % 60
        val hours = diff / (1000 * 60 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatStandardInOut(
        inTime: Long,
        outTime: Long?,
        pendingLabel: String = "Still Inside / Pending"
    ): String {
        val inStr = formatStandardDateTime(inTime)
        return if (outTime != null) {
            val outStr = formatStandardDateTime(outTime)
            val dur = formatDuration(inTime, outTime)
            "IN: $inStr | OUT: $outStr (Duration: $dur)"
        } else {
            "IN: $inStr | OUT: $pendingLabel"
        }
    }
}

/**
 * Standardized IN/OUT timestamp display composable for all GateAI modules.
 * Strictly adheres to format:
 * - When Completed: "IN: 13 Sep 2026, 10:30 AM | OUT: 13 Sep 2026, 02:15 PM (Duration: 3h 45m)"
 * - When Active: "IN: 13 Sep 2026, 10:30 AM" with active status badge "OUT: Still Inside / Pending"
 */
@Composable
fun StandardInOutDisplay(
    inTime: Long,
    outTime: Long?,
    modifier: Modifier = Modifier,
    activeLabel: String = "Still Inside / Pending",
    showDuration: Boolean = true
) {
    val inStr = DateTimeUtils.formatStandardDateTime(inTime)
    val isCompleted = outTime != null

    if (isCompleted) {
        val outStr = DateTimeUtils.formatStandardDateTime(outTime!!)
        val durationStr = DateTimeUtils.formatDuration(inTime, outTime)
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "IN: $inStr  |  OUT: $outStr" + if (showDuration) " (Duration: $durationStr)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        val liveElapsed = DateTimeUtils.formatDuration(inTime, System.currentTimeMillis())
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IN: $inStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OUT: $activeLabel ($liveElapsed)",
                        color = Color(0xFFB45309),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Interactive Date & Time Picker Field for GateAI forms.
 * Allows guards/users to view, manually adjust, or backdate entry/exit dates & times.
 * Includes DatePickerDialog + TimePickerDialog triggers, visual backdated badge, and quick "NOW" reset.
 */
@Composable
fun DateTimePickerField(
    label: String,
    timestamp: Long,
    onTimestampChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    includeTime: Boolean = true,
    minTimestamp: Long? = null,
    errorMessage: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    testTagPrefix: String = "datetime"
) {
    val context = LocalContext.current
    val isBackdated = remember(timestamp) {
        timestamp > 0L && timestamp < System.currentTimeMillis() - (10 * 60 * 1000)
    }

    val isFuture = remember(timestamp) {
        timestamp > System.currentTimeMillis() + (10 * 60 * 1000)
    }

    val validationError = errorMessage ?: if (minTimestamp != null && minTimestamp > 0L && timestamp < minTimestamp) {
        "Time cannot be earlier than ${if (includeTime) DateTimeUtils.formatStandardDateTime(minTimestamp) else DateTimeUtils.formatStandardDate(minTimestamp)}"
    } else null

    val isError = validationError != null

    fun showDatePicker(thenTimePicker: Boolean = includeTime) {
        if (!enabled) return
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        }
        val datePicker = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                if (thenTimePicker) {
                    val timePicker = android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            cal.set(Calendar.MINUTE, minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            onTimestampChanged(cal.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    )
                    timePicker.show()
                } else {
                    if (!includeTime) {
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                    }
                    onTimestampChanged(cal.timeInMillis)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        if (minTimestamp != null && minTimestamp > 0L) {
            datePicker.datePicker.minDate = minTimestamp
        }
        datePicker.show()
    }

    fun showTimePickerOnly() {
        if (!enabled || !includeTime) return
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        }
        val timePicker = android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onTimestampChanged(cal.timeInMillis)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )
        timePicker.show()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isBackdated) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BACKDATED",
                            color = Color(0xFFB45309),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isFuture) {
                    Surface(
                        color = Color(0xFFEDE9FE),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "FUTURE",
                            color = Color(0xFF6D28D9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { showDatePicker() }
                .testTag("${testTagPrefix}_picker_field"),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Calendar",
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Column {
                        val displayValue = if (timestamp > 0L) {
                            if (includeTime) DateTimeUtils.formatStandardDateTime(timestamp)
                            else DateTimeUtils.formatStandardDate(timestamp)
                        } else "Select Date & Time"

                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (includeTime) "Tap to adjust Date & Time" else "Tap to adjust Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (includeTime) {
                        IconButton(
                            onClick = { showTimePickerOnly() },
                            modifier = Modifier.size(34.dp),
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Pick Time Only",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { onTimestampChanged(System.currentTimeMillis()) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("${testTagPrefix}_now_button"),
                        shape = RoundedCornerShape(6.dp),
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Now",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (validationError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = validationError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        } else if (helperText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

