package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MaterialMovement
import com.example.data.local.MaterialMovementItem
import com.example.data.local.RepairRecord
import com.example.data.local.VehicleEntry
import com.example.data.local.VisitorEntry
import com.example.util.DateTimeUtils
import com.example.viewmodel.GateAiViewModel
import com.example.viewmodel.ScannedEntityResult
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: GateAiViewModel,
    mode: String = "ALL", // "ALL", "VISITOR", "MATERIAL", "VEHICLE"
    onNavigateBack: () -> Unit,
    onVisitorPassScanned: (String) -> Unit = {},
    onMaterialCodeScanned: (String) -> Unit = {},
    onVehicleScanned: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Camera controls
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Detected barcode feedback state
    var detectedCode by remember { mutableStateOf<String?>(null) }
    var detectedBoundingBox by remember { mutableStateOf<Rect?>(null) }
    var resolvedResult by remember { mutableStateOf<ScannedEntityResult?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // Manual input / demo fallback
    var showManualInput by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    val insideVisitors by viewModel.insideVisitors.collectAsStateWithLifecycle()
    val currentlyOutside by viewModel.currentlyOutside.collectAsStateWithLifecycle()
    val pendingRepairs by viewModel.pendingRepairs.collectAsStateWithLifecycle()
    val insideVehicles by viewModel.insideVehicles.collectAsStateWithLifecycle()

    fun triggerVibration() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun handleCodeScanned(code: String, bounds: Rect? = null) {
        if (isPaused || isResolving || code.isBlank()) return
        isPaused = true
        detectedCode = code
        detectedBoundingBox = bounds
        triggerVibration()

        coroutineScope.launch {
            isResolving = true
            val res = viewModel.resolveScannedCode(code)
            resolvedResult = res
            isResolving = false

            // Auto-action if in specific caller mode:
            if (mode == "VISITOR" && res is ScannedEntityResult.Visitor) {
                delay(400)
                onVisitorPassScanned(res.entry.passId)
            } else if (mode == "MATERIAL" && (res is ScannedEntityResult.Material || res is ScannedEntityResult.Repair)) {
                delay(400)
                onMaterialCodeScanned(code)
            } else if (mode == "VEHICLE" && res is ScannedEntityResult.Vehicle) {
                delay(400)
                onVehicleScanned(res.entry.vehicleNumber)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (mode) {
                                "VISITOR" -> "Scan Visitor Pass"
                                "MATERIAL" -> "Scan Material / Challan"
                                "VEHICLE" -> "Scan Vehicle Pass"
                                else -> "GateAI QR / Barcode Scanner"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            "Real-time Camera Verification",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scanner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Flashlight / Torch Toggle
                    if (camera != null && hasCameraPermission) {
                        IconButton(
                            onClick = {
                                val next = !isTorchOn
                                isTorchOn = next
                                camera?.cameraControl?.enableTorch(next)
                            },
                            modifier = Modifier.testTag("btn_toggle_torch")
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Torch",
                                tint = if (isTorchOn) Color(0xFFFBBF24) else Color.White
                            )
                        }
                    }

                    // Keyboard input manual fallback
                    IconButton(
                        onClick = { showManualInput = !showManualInput },
                        modifier = Modifier.testTag("btn_manual_code_input")
                    ) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Manual Code Entry",
                            tint = if (showManualInput) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCameraPermission) {
                // Permission Denied UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "GateAI uses the camera to scan Visitor Gate Passes, Material Inward QR codes, and Repair Challan barcodes for instant automated verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_grant_camera_permission"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showManualInput = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Code Manually")
                    }
                }
            } else {
                // Fullscreen Camera Preview
                CameraPreviewView(
                    isPaused = isPaused,
                    onBarcodeScanned = { rawVal, box ->
                        handleCodeScanned(rawVal, box)
                    },
                    onCameraBound = { boundCam ->
                        camera = boundCam
                    }
                )

                // Viewfinder Overlay with Animated Reticle
                ViewfinderOverlay(
                    modifier = Modifier.fillMaxSize(),
                    mode = mode,
                    isPaused = isPaused,
                    detectedBounds = detectedBoundingBox
                )

                // Bottom Controls & Quick Test Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Instruction text
                    Text(
                        when (mode) {
                            "VISITOR" -> "Point at Visitor Pass QR code for instant checkout"
                            "MATERIAL" -> "Point at Material Consignment QR or Challan Barcode"
                            else -> "Align QR Code or Barcode inside the viewfinder frame"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Test / Demo chips for emulator or instant testing
                    Text(
                        "🧪 QUICK DEMO TEST CODES (TAP TO SIMULATE SCAN):",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (mode == "VISITOR" || mode == "ALL") {
                            items(insideVisitors.take(4)) { v ->
                                SuggestionChip(
                                    onClick = { handleCodeScanned(v.passId) },
                                    label = { Text("Pass ${v.passId} (${v.visitorName.take(10)})", fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF1F2937),
                                        labelColor = Color(0xFF34D399)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = Color(0xFF059669)
                                    )
                                )
                            }
                        }

                        if (mode == "MATERIAL" || mode == "ALL") {
                            items(currentlyOutside.take(3)) { m ->
                                SuggestionChip(
                                    onClick = { handleCodeScanned(m.movementId) },
                                    label = { Text(m.movementId, fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF1E293B),
                                        labelColor = Color(0xFF60A5FA)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = Color(0xFF2563EB)
                                    )
                                )
                            }
                            items(pendingRepairs.take(2)) { r ->
                                SuggestionChip(
                                    onClick = { handleCodeScanned(r.repairId) },
                                    label = { Text("Repair ${r.repairId}", fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF3B1F2B),
                                        labelColor = Color(0xFFF472B6)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = Color(0xFFDB2777)
                                    )
                                )
                            }
                        }

                        if (mode == "VEHICLE" || mode == "ALL") {
                            items(insideVehicles.take(2)) { veh ->
                                SuggestionChip(
                                    onClick = { handleCodeScanned(veh.vehicleNumber) },
                                    label = { Text(veh.vehicleNumber, fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF2E2619),
                                        labelColor = Color(0xFFFBBF24)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = Color(0xFFD97706)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Manual Code Entry Dialog
            if (showManualInput) {
                AlertDialog(
                    onDismissRequest = { showManualInput = false },
                    title = { Text("Manual Barcode / Pass ID Entry", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Enter the Pass ID (e.g. VP-04921) or Material Consignment ID:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = manualCodeInput,
                                onValueChange = { manualCodeInput = it.uppercase() },
                                label = { Text("Pass ID / Barcode #") },
                                placeholder = { Text("e.g. VP-123456 or MM-1234") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_manual_barcode"),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (manualCodeInput.isNotBlank()) {
                                        showManualInput = false
                                        handleCodeScanned(manualCodeInput)
                                    }
                                })
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (manualCodeInput.isNotBlank()) {
                                    showManualInput = false
                                    handleCodeScanned(manualCodeInput)
                                }
                            },
                            enabled = manualCodeInput.isNotBlank(),
                            modifier = Modifier.testTag("btn_submit_manual_barcode")
                        ) {
                            Text("Verify Code")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualInput = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Scanned Result Bottom Sheet Modal
            if (resolvedResult != null) {
                ScannedResultSheet(
                    result = resolvedResult!!,
                    rawCode = detectedCode ?: "",
                    mode = mode,
                    onDismiss = {
                        resolvedResult = null
                        detectedCode = null
                        detectedBoundingBox = null
                        isPaused = false
                    },
                    onCheckoutVisitor = { passId ->
                        resolvedResult = null
                        onVisitorPassScanned(passId)
                    },
                    onOpenMaterialHub = { code ->
                        resolvedResult = null
                        onMaterialCodeScanned(code)
                    },
                    onVehicleCheckout = { vehicleNum ->
                        resolvedResult = null
                        onVehicleScanned(vehicleNum)
                    }
                )
            }
        }
    }
}

/**
 * CameraX Live Preview with ImageAnalysis for Barcode Scanning
 */
@Composable
fun CameraPreviewView(
    isPaused: Boolean,
    onBarcodeScanned: (String, Rect?) -> Unit,
    onCameraBound: (Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val isPausedState = rememberUpdatedState(isPaused)
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (_: Exception) {}
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {}
            try {
                barcodeScanner.close()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef = cameraProvider

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    var lastAnalyzedTimestamp = 0L

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        // Throttle frame processing to max ~4 FPS to avoid high-frequency IPC / audit log saturation
                        if (isPausedState.value || currentTime - lastAnalyzedTimestamp < 250L) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        lastAnalyzedTimestamp = currentTime

                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank() && !isPausedState.value) {
                                            val bounds = barcode.boundingBox
                                            onBarcodeScanned(rawValue, bounds)
                                            break
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w("QrScanner", "MLKit Barcode error: ${e.message}")
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()

                    val cameraSelector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> null
                    }

                    if (cameraSelector != null) {
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        onCameraBound(camera)
                    }
                } catch (e: Exception) {
                    Log.e("QrScanner", "Error binding CameraX", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Visual Viewfinder with Darkened Mask, Corner Reticles, and Scanning Laser Animation
 */
@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    mode: String,
    isPaused: Boolean,
    detectedBounds: Rect?
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Define viewfinder box dimensions
        val boxWidth = (canvasWidth * 0.72f).coerceIn(240.dp.toPx(), 320.dp.toPx())
        val boxHeight = boxWidth // square frame for QR and barcodes
        val left = (canvasWidth - boxWidth) / 2f
        val top = (canvasHeight - boxHeight) / 2.4f

        // 1. Darkened outer background with cutout
        with(drawContext.canvas.nativeCanvas) {
            val checkPoint = saveLayer(null, null)

            // Fill full canvas with 65% dark overlay
            drawRect(
                color = Color.Black.copy(alpha = 0.65f),
                topLeft = Offset.Zero,
                size = size
            )

            // Cut out transparent rounded rectangle in center
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            restoreToCount(checkPoint)
        }

        // 2. Corner Target Brackets (Neon Green / Cyan)
        val cornerColor = if (isPaused) Color(0xFF10B981) else Color(0xFF06B6D4)
        val cornerLength = 28.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val radius = 16.dp.toPx()

        // Top-Left Corner
        drawLine(cornerColor, Offset(left + radius, top), Offset(left + cornerLength, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top + radius), Offset(left, top + cornerLength), cornerStroke)
        drawArc(
            color = cornerColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(radius * 2, radius * 2),
            style = Stroke(cornerStroke)
        )

        // Top-Right Corner
        val right = left + boxWidth
        drawLine(cornerColor, Offset(right - cornerLength, top), Offset(right - radius, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top + radius), Offset(right, top + cornerLength), cornerStroke)
        drawArc(
            color = cornerColor,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - radius * 2, top),
            size = Size(radius * 2, radius * 2),
            style = Stroke(cornerStroke)
        )

        // Bottom-Left Corner
        val bottom = top + boxHeight
        drawLine(cornerColor, Offset(left + radius, bottom), Offset(left + cornerLength, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom - cornerLength), Offset(left, bottom - radius), cornerStroke)
        drawArc(
            color = cornerColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, bottom - radius * 2),
            size = Size(radius * 2, radius * 2),
            style = Stroke(cornerStroke)
        )

        // Bottom-Right Corner
        drawLine(cornerColor, Offset(right - cornerLength, bottom), Offset(right - radius, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom - cornerLength), Offset(right, bottom - radius), cornerStroke)
        drawArc(
            color = cornerColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - radius * 2, bottom - radius * 2),
            size = Size(radius * 2, radius * 2),
            style = Stroke(cornerStroke)
        )

        // 3. Sweeping Laser Beam (when active)
        if (!isPaused) {
            val laserY = top + (boxHeight * laserProgress)
            drawLine(
                color = Color(0xFF10B981).copy(alpha = 0.9f),
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
            // Subtle glow around laser
            drawRect(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                topLeft = Offset(left + 8.dp.toPx(), laserY - 6.dp.toPx()),
                size = Size(boxWidth - 16.dp.toPx(), 12.dp.toPx())
            )
        } else {
            // Target locked indicator
            drawRoundRect(
                color = Color(0xFF10B981).copy(alpha = 0.25f),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
        }
    }
}

/**
 * Bottom Sheet displaying verified entity details and 1-tap action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedResultSheet(
    result: ScannedEntityResult,
    rawCode: String,
    mode: String,
    onDismiss: () -> Unit,
    onCheckoutVisitor: (String) -> Unit,
    onOpenMaterialHub: (String) -> Unit,
    onVehicleCheckout: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            when (result) {
                is ScannedEntityResult.Visitor -> {
                    val entry = result.entry
                    val isInside = result.isInside

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isInside) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isInside) Icons.Default.Badge else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isInside) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("VISITOR PASS VERIFIED", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF047857))
                            Text(entry.visitorName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Pass #${entry.passId}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Surface(
                            color = if (isInside) Color(0xFF10B981) else Color(0xFF6B7280),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                if (isInside) "INSIDE" else "COMPLETED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Host / Meeting With:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(entry.host, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            if (entry.company.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Company:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(entry.company, fontSize = 13.sp)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mobile #:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(entry.mobileNumber, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Inward Entry Time:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(DateTimeUtils.formatStandardDateTime(entry.inTime), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isInside) {
                        Button(
                            onClick = { onCheckoutVisitor(entry.passId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_action_checkout_visitor"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PROCEED TO VISITOR CHECKOUT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Pass Already Checked Out • Scan Another")
                        }
                    }
                }

                is ScannedEntityResult.Material -> {
                    val movement = result.movement
                    val items = result.items
                    val isOutward = movement.movementType == "OUT"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = (if (isOutward) Color(0xFF2563EB) else Color(0xFF059669)).copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isOutward) Icons.Default.Output else Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = if (isOutward) Color(0xFF2563EB) else Color(0xFF059669),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("CONSIGNMENT RECOGNIZED", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(movement.movementId, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                if (isOutward) "OUT: ${movement.destinationParty.ifBlank { movement.supplierName }}"
                                else "IN: ${movement.supplierName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Surface(
                            color = if (movement.status == "OUTSIDE") Color(0xFFD97706) else Color(0xFF059669),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                movement.status,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (movement.invoiceNumber.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Invoice #:", style = MaterialTheme.typography.bodySmall)
                                    Text(movement.invoiceNumber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                            if (movement.challanNumber.isNotBlank() || movement.referenceDocument.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Challan / Ref #:", style = MaterialTheme.typography.bodySmall)
                                    Text(movement.challanNumber.ifBlank { movement.referenceDocument }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                            if (movement.vehicleNumber.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Vehicle #:", style = MaterialTheme.typography.bodySmall)
                                    Text(movement.vehicleNumber, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                            }
                            if (items.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Consignment Items (${items.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                items.take(3).forEach { item ->
                                    Text("• ${item.materialDescription} (${item.quantity} ${item.uom})", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onOpenMaterialHub(movement.movementId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_action_open_material_hub"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.ManageSearch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIEW CONSIGNMENT IN MATERIAL HUB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                is ScannedEntityResult.Repair -> {
                    val rep = result.record
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFDB2777).copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFDB2777), modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("REPAIR RECORD FOUND", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFDB2777))
                            Text(rep.repairId, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                            Text(rep.materialDescription, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Surface(
                            color = if (rep.status == "PENDING") Color(0xFFD97706) else Color(0xFF059669),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(rep.status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Vendor: ${rep.repairVendor}", fontSize = 13.sp)
                            Text("Reason: ${rep.repairReason}", fontSize = 13.sp)
                            if (rep.assetOrSerial.isNotBlank()) {
                                Text("Asset / Serial #: ${rep.assetOrSerial}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Dispatched: ${DateTimeUtils.formatStandardDateTime(rep.sentDate)}", fontSize = 12.sp)
                            Text("Expected Return: ${DateTimeUtils.formatStandardDate(rep.expectedReturnDate)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onOpenMaterialHub(rep.repairId) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB2777))
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIEW REPAIR IN MATERIAL HUB", fontWeight = FontWeight.Bold)
                    }
                }

                is ScannedEntityResult.Vehicle -> {
                    val v = result.entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VEHICLE RECOGNIZED", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(v.vehicleNumber, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                            Text("Driver: ${v.driverName}", fontSize = 13.sp)
                        }
                        Surface(
                            color = if (result.isInside) Color(0xFF10B981) else Color(0xFF6B7280),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(v.status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (result.isInside) {
                        Button(
                            onClick = { onVehicleCheckout(v.vehicleNumber) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PROCEED TO VEHICLE OUT", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Vehicle Completed Trip • Scan Another")
                        }
                    }
                }

                is ScannedEntityResult.Unknown -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UNRECOGNIZED CODE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            Text(rawCode.take(24), fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                            Text(result.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Scan Again")
                        }
                        Button(
                            onClick = {
                                onOpenMaterialHub(rawCode)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Search in Hub")
                        }
                    }
                }
            }
        }
    }
}
