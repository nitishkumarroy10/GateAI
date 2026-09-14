package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.GateResult
import com.example.data.local.VisitorEntry
import com.example.util.DateTimeUtils
import com.example.util.StandardInOutDisplay
import com.example.viewmodel.GateAiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorOutScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToQrScanner: () -> Unit = {}
) {
    val insideVisitors by viewModel.insideVisitors.collectAsStateWithLifecycle()
    val scannedVisitorPass by viewModel.scannedVisitorPass.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkedOutEntry by remember { mutableStateOf<VisitorEntry?>(null) }

    LaunchedEffect(scannedVisitorPass, insideVisitors) {
        if (!scannedVisitorPass.isNullOrBlank()) {
            val passId = scannedVisitorPass!!
            viewModel.clearScannedVisitorPass()
            val match = insideVisitors.find { it.passId.equals(passId, ignoreCase = true) }
            
            if (match != null) {
                viewModel.visitorOut(match.entryId, outTime = System.currentTimeMillis()) { res ->
                    when (res) {
                        is GateResult.Success -> {
                            checkedOutEntry = res.data
                        }
                        is GateResult.Error -> {
                            errorMessage = res.message
                        }
                    }
                }
            } else {
                errorMessage = "Pass ID ($passId) not found or visitor already exited."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visitor Exit", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (checkedOutEntry != null) {
            val v = checkedOutEntry!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "CHECKOUT SUCCESSFUL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(v.visitorName, fontWeight = FontWeight.Black, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Duration: ${DateTimeUtils.formatDuration(v.inTime, v.outTime ?: System.currentTimeMillis())}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("done_button"),
                    shape = CircleShape
                ) {
                    Text("DONE", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToQrScanner() }
                        .testTag("card_scan_visitor_qr"),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "SCAN PASS TO EXIT",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SleekTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search manual checkout...",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("search_visitor_out")
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val filtered = if (searchQuery.isBlank()) {
                    insideVisitors
                } else {
                    insideVisitors.filter {
                        it.visitorName.contains(searchQuery, ignoreCase = true) ||
                        it.mobileNumber.contains(searchQuery)
                    }
                }
                
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No visitors found inside.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered) { v ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(v.visitorName, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Host: ${v.host}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            viewModel.visitorOut(v.passId, outTime = System.currentTimeMillis()) { res ->
                                                when (res) {
                                                    is GateResult.Success -> {
                                                        checkedOutEntry = res.data
                                                    }
                                                    is GateResult.Error -> {
                                                        errorMessage = res.message
                                                    }
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Text("EXIT", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
