package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.data.local.GateAiDatabase
import com.example.data.local.GateAiRepository
import com.example.data.sync.SyncManager
import com.example.ui.navigation.GateAiNavGraph
import com.example.ui.theme.GateAITheme
import com.example.viewmodel.GateAiViewModel
import com.example.viewmodel.GateAiViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = GateAiDatabase.getDatabase(this)
        val repository = GateAiRepository(database)
        val syncManager = SyncManager(this, repository)
        
        setContent {
            GateAITheme {
                val navController = rememberNavController()
                val viewModel: GateAiViewModel = viewModel(
                    factory = GateAiViewModelFactory(repository, syncManager)
                )
                
                GateAiNavGraph(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}
