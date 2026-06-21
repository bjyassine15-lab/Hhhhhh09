package com.example.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.PosViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import android.media.AudioManager
import android.media.ToneGenerator
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Helper method for barcode verification (100% correct EAN-13 & EAN-8 checksum check and basic length logic)
private fun isValidBarcode(code: String): Boolean {
    val trimmed = code.trim()
    if (trimmed.length < 4) return false
    if (trimmed.all { it.isDigit() }) {
        if (trimmed.length == 13) {
            var sum = 0
            for (i in 0..11) {
                val digit = trimmed[i] - '0'
                sum += if (i % 2 == 0) digit else digit * 3
            }
            val checkDigit = (10 - (sum % 10)) % 10
            val originalCheck = trimmed[12] - '0'
            return checkDigit == originalCheck
        }
        if (trimmed.length == 8) {
            var sum = 0
            for (i in 0..6) {
                val digit = trimmed[i] - '0'
                sum += if (i % 2 == 0) digit * 3 else digit
            }
            val checkDigit = (10 - (sum % 10)) % 10
            val originalCheck = trimmed[7] - '0'
            return checkDigit == originalCheck
        }
    }
    return true
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScannerView(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String, (Boolean) -> Unit) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("camera_settings_prefs", Context.MODE_PRIVATE) }
    
    var antiBlurDelay by remember { mutableStateOf(sharedPrefs.getBoolean("camera_anti_blur_delay", false)) }
    var continuousAutofocus by remember { mutableStateOf(sharedPrefs.getBoolean("camera_continuous_autofocus", true)) }
    var exposureIndex by remember { mutableIntStateOf(sharedPrefs.getInt("camera_exposure_index", 0)) }
    
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = modifier) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewAndScanner(
                onBarcodeDetected = onBarcodeDetected,
                antiBlurDelay = antiBlurDelay,
                continuousAutofocus = continuousAutofocus,
                exposureIndex = exposureIndex,
                modifier = Modifier.matchParentSize()
            )
            // Beautiful scanner overlay targeting/aiming window
            ScannerOverlay(modifier = Modifier.matchParentSize())

            // Professional Gear Settings Icon Floating in bottom-left corner
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .border(1.dp, Color(0xFFFF9800), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "إعدادات الكاميرا",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "صلاحية الكاميرا مطلوبة لمسح الباركود",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("طلب صلاحية الكاميرا", color = Color.White)
                }
            }
        }
    }

    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(BorderStroke(1.dp, Color(0xFF262626)), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "إعدادات الكاميرا المتقدمة",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    HorizontalDivider(color = Color(0xFF262626))
                    
                    // Anti-blur Delay Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "التأخير المضاد للضبابية (500ms)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "يضيف تأخيراً بسيطاً عند فتح الكاميرا لتركيز العدسة وتفادي الضبابية",
                                color = Color(0xFF8A8A8A),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = antiBlurDelay,
                            onCheckedChange = { checked ->
                                antiBlurDelay = checked
                                sharedPrefs.edit().putBoolean("camera_anti_blur_delay", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF9800),
                                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.4f),
                                uncheckedThumbColor = Color(0xFF8A8A8A),
                                uncheckedTrackColor = Color(0xFF262626)
                            )
                        )
                    }
                    
                    HorizontalDivider(color = Color(0xFF262626).copy(alpha = 0.5f))
                    
                    // Continuous Autofocus Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "التركيز التلقائي المستمر",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "إبقاء العدسة مركزة باستمرار على المنتج لمسح أسرع",
                                color = Color(0xFF8A8A8A),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = continuousAutofocus,
                            onCheckedChange = { checked ->
                                continuousAutofocus = checked
                                sharedPrefs.edit().putBoolean("camera_continuous_autofocus", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF9800),
                                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.4f),
                                uncheckedThumbColor = Color(0xFF8A8A8A),
                                uncheckedTrackColor = Color(0xFF262626)
                            )
                        )
                    }
                    
                    HorizontalDivider(color = Color(0xFF262626).copy(alpha = 0.5f))
                    
                    // Exposure compensation Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "مستوى حساسية الضوء (Exposure)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "لتحسين الرؤية ومقاومة لمعان الأسطح والورق البلاستيكي",
                            color = Color(0xFF8A8A8A),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("-", color = Color(0xFF8A8A8A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = exposureIndex.toFloat(),
                                onValueChange = { value ->
                                    val index = value.roundToInt()
                                    exposureIndex = index
                                    sharedPrefs.edit().putInt("camera_exposure_index", index).apply()
                                },
                                valueRange = -4f..4f,
                                steps = 7,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF9800),
                                    activeTrackColor = Color(0xFFFF9800),
                                    inactiveTrackColor = Color(0xFF262626)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text("+", color = Color(0xFF8A8A8A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إغلاق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewAndScanner(
    onBarcodeDetected: (String, (Boolean) -> Unit) -> Unit,
    antiBlurDelay: Boolean,
    continuousAutofocus: Boolean,
    exposureIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Find activity level LifecycleOwner to prevent black screens inside dialogs!
    val lifecycleOwner = remember(context) {
        var curContext = context
        var foundOwner: androidx.lifecycle.LifecycleOwner? = null
        while (curContext is android.content.ContextWrapper) {
            if (curContext is androidx.lifecycle.LifecycleOwner) {
                foundOwner = curContext
                break
            }
            curContext = curContext.baseContext
        }
        foundOwner ?: (context as? androidx.lifecycle.LifecycleOwner) ?: error("No LifecycleOwner found")
    }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    // Strict 1D Barcode Formats for maximum speed and accuracy
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39
            )
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    // Atomic Processing Lock so that we never send scans concurrently
    val isProcessing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Strict 1.5 seconds cooldown after ANY successful scan (allows users to move product away)
    var lastScannedTime by remember { mutableLongStateOf(0L) }
    val cooldownMs = 1500L

    // Anti-blur Delay state manager: Delays frame parsing by 500ms to let lens focus
    var isInitialDelayOver by remember { mutableStateOf(!antiBlurDelay) }
    LaunchedEffect(antiBlurDelay) {
        if (antiBlurDelay) {
            isInitialDelayOver = false
            delay(500L)
            isInitialDelayOver = true
        } else {
            isInitialDelayOver = true
        }
    }

    // Buffer clearing function
    fun clearBarcodeBuffer() {
        Log.d("BarcodeProcessor", "Clearing scanner buffer. Ready for next scan!")
        isProcessing.set(false)
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraExecutor.shutdown()
                scanner.close()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } else {
                    cameraProviderFuture.addListener({
                        try {
                            cameraProviderFuture.get().unbindAll()
                        } catch (e: Exception) {
                            Log.e("CameraPreviewAndScanner", "Error unbinding camera on disposal", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            } catch (exc: Exception) {
                Log.e("CameraPreviewAndScanner", "Failed to unbind camera provider on dispose", exc)
            }
        }
    }

    // Capture reference to bind focus/exposure dynamically
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var minExposure by remember { mutableIntStateOf(-4) }
    var maxExposure by remember { mutableIntStateOf(4) }
    var isExposureSupported by remember { mutableStateOf(false) }

    // Dynamically apply Exposure index changes live to use cases
    LaunchedEffect(cameraControl, exposureIndex, isExposureSupported) {
        if (isExposureSupported && cameraControl != null) {
            try {
                val clamped = exposureIndex.coerceIn(minExposure, maxExposure)
                cameraControl?.setExposureCompensationIndex(clamped)
            } catch (e: Exception) {
                Log.e("CameraPreviewAndScanner", "Failed to apply exposure compensation live", e)
            }
        }
    }

    // Periodically run Continuous Autofocus trigger if active
    LaunchedEffect(cameraControl, continuousAutofocus) {
        if (continuousAutofocus && cameraControl != null) {
            while (true) {
                try {
                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    val point = factory.createPoint(0.5f, 0.5f)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    cameraControl?.startFocusAndMetering(action)
                } catch (e: Exception) {
                    Log.e("CameraPreviewAndScanner", "Focus trigger exception", e)
                }
                delay(2000L)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview Use Case set to crisp 720p for optimal line contrast
                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                // ImageAnalysis Use Case set to 720p with non-blocking latest frames
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    // 0. Delay processing if initial focusing delay is not over yet
                    if (!isInitialDelayOver) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    @SuppressLint("UnsafeOptInUsageError")
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        // 1. Check Atomic Processing Lock
                        if (isProcessing.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val code = barcode.rawValue
                                    if (!code.isNullOrEmpty() && isValidBarcode(code)) {
                                        val now = System.currentTimeMillis()
                                        
                                        // 2. Strict cooldown check (1.5 seconds) after any scan
                                        if (now - lastScannedTime < cooldownMs) {
                                            break
                                        }

                                        // 3. Atomically acquire scanning lock to run "One Scan = One Action"
                                        if (isProcessing.compareAndSet(false, true)) {
                                            onBarcodeDetected(code) { isSuccess ->
                                                if (isSuccess) {
                                                    lastScannedTime = System.currentTimeMillis()
                                                    clearBarcodeBuffer()
                                                } else {
                                                    // Immediately reset lock on error/failure
                                                    isProcessing.set(false)
                                                }
                                            }
                                        }
                                        break // Only process first valid barcode found in this frame
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("CameraPreviewAndScanner", "MLKit scanning error: ", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                // Use Back Camera as default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    
                    cameraControl = camera.cameraControl
                    val exposureState = camera.cameraInfo.exposureState
                    isExposureSupported = exposureState.isExposureCompensationSupported
                    if (isExposureSupported) {
                        minExposure = exposureState.exposureCompensationRange.lower
                        maxExposure = exposureState.exposureCompensationRange.upper
                    }
                    
                    camera.cameraControl.cancelFocusAndMetering()
                } catch (exc: Exception) {
                    Log.e("CameraPreviewAndScanner", "Camera X binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Scanner window box dimension
            val boxWidth = canvasWidth * 0.70f
            val boxHeight = canvasHeight * 0.45f
            
            val left = (canvasWidth - boxWidth) / 2
            val top = (canvasHeight - boxHeight) / 2

            // Draw translucent dark background chunks around scanning window to form a perfect cutout
            // Let's use 60% opacity for a nice professional dimming
            val dimColor = Color.Black.copy(alpha = 0.60f)
            // Top overlay section
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, top)
            )
            // Bottom overlay section
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, top + boxHeight),
                size = Size(canvasWidth, canvasHeight - (top + boxHeight))
            )
            // Left overlay section
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, top),
                size = Size(left, boxHeight)
            )
            // Right overlay section
            drawRect(
                color = dimColor,
                topLeft = Offset(left + boxWidth, top),
                size = Size(canvasWidth - (left + boxWidth), boxHeight)
            )

            val cyberCyan = Color(0xFF4CAF50)

            // Draw a beautiful bright border around the scanning window
            drawRoundRect(
                color = cyberCyan.copy(alpha = 0.3f),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Draw thick glowing corner targets for that professional scanner feel
            val lineLen = 22.dp.toPx()
            val strokeW = 4.dp.toPx()

            // Top-Left corner
            // Horizontal stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left, top),
                size = Size(lineLen, strokeW)
            )
            // Vertical stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left, top),
                size = Size(strokeW, lineLen)
            )

            // Top-Right corner
            // Horizontal stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left + boxWidth - lineLen, top),
                size = Size(lineLen, strokeW)
            )
            // Vertical stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left + boxWidth - strokeW, top),
                size = Size(strokeW, lineLen)
            )

            // Bottom-Left corner
            // Horizontal stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left, top + boxHeight - strokeW),
                size = Size(lineLen, strokeW)
            )
            // Vertical stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left, top + boxHeight - lineLen),
                size = Size(strokeW, lineLen)
            )

            // Bottom-Right corner
            // Horizontal stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left + boxWidth - lineLen, top + boxHeight - strokeW),
                size = Size(lineLen, strokeW)
            )
            // Vertical stroke
            drawRect(
                color = cyberCyan,
                topLeft = Offset(left + boxWidth - strokeW, top + boxHeight - lineLen),
                size = Size(strokeW, lineLen)
            )
        }

        // Help text guiding the user
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "ضع الباركود في الإطار للمسح تلقائياً",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
