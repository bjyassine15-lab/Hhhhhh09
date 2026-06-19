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

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewAndScanner(
                onBarcodeDetected = onBarcodeDetected,
                modifier = Modifier.fillMaxSize()
            )
            // Beautiful scanner overlay targeting/aiming window
            ScannerOverlay(modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
}

@Composable
fun CameraPreviewAndScanner(
    onBarcodeDetected: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // 1.5 seconds local scan cooldown for the EXACT same barcode
    var lastScannedCode by remember { mutableStateOf<String?>(null) }
    var lastScannedTime by remember { mutableLongStateOf(0L) }
    val cooldownMs = 1500L

    // Buffer clearing function as requested
    fun clearBarcodeBuffer() {
        Log.d("BarcodeProcessor", "Clearing scanner buffer. Ready for next scan!")
        isProcessing.set(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
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
                                        
                                        // 2. Cooldown check per unique barcode (1.5 seconds)
                                        if (code == lastScannedCode && (now - lastScannedTime < cooldownMs)) {
                                            break
                                        }

                                        // 3. Atomically acquire scanning lock to run "One Scan = One Action"
                                        if (isProcessing.compareAndSet(false, true)) {
                                            // Handle barcode with callback as requested (Check -> Add -> Buffer Clear -> Cooldown)
                                            onBarcodeDetected(code) { isSuccess ->
                                                if (isSuccess) {
                                                    lastScannedCode = code
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
                    
                    // Force Continuous Auto Focus picture/video mode
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
            // Top overlay section
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, top)
            )
            // Bottom overlay section
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, top + boxHeight),
                size = Size(canvasWidth, canvasHeight - (top + boxHeight))
            )
            // Left overlay section
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, top),
                size = Size(left, boxHeight)
            )
            // Right overlay section
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(left + boxWidth, top),
                size = Size(canvasWidth - (left + boxWidth), boxHeight)
            )

            // Draw a beautiful bright border around the scanning window
            drawRoundRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
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
