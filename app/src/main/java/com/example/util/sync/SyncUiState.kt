package com.example.util.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SyncStatusIndicator(syncInfo: SyncInfo) {
    Surface(
        color = when (syncInfo.state) {
            SyncState.SYNCING -> MaterialTheme.colorScheme.primaryContainer
            SyncState.ERROR -> MaterialTheme.colorScheme.errorContainer
            SyncState.OFFLINE -> Color.Gray
            SyncState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (syncInfo.state) {
                    SyncState.SYNCING -> "Syncing... (${syncInfo.pendingCount} pending)"
                    SyncState.ERROR -> "Sync Failed"
                    SyncState.OFFLINE -> "Offline (${syncInfo.pendingCount} pending)"
                    SyncState.IDLE -> if (syncInfo.pendingCount > 0) "${syncInfo.pendingCount} Pending Sync" else "Synced"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (syncInfo.state) {
                    SyncState.SYNCING -> MaterialTheme.colorScheme.onPrimaryContainer
                    SyncState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                    SyncState.OFFLINE -> Color.White
                    SyncState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
