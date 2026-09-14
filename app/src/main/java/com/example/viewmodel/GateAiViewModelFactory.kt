package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.GateAiRepository
import com.example.data.sync.SyncManager

class GateAiViewModelFactory(
    private val repository: GateAiRepository,
    private val syncManager: SyncManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GateAiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GateAiViewModel(repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
