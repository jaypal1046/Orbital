package com.orbital.bridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QrCodeScannerView(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "📷 Camera Permission Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "To securely pair with your Laptop AI Bridge and scan the Bitcoin-grade encrypted QR code, please grant camera access.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Camera Permission", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    var isScanned by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = Executors.newSingleThreadExecutor()

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !isScanned) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank() && !isScanned) {
                                            isScanned = true
                                            onQrScanned(rawValue)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("QrCodeScannerView", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with scanning frame & laser animation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val frameSize = canvasWidth * 0.70f
            val left = (canvasWidth - frameSize) / 2
            val top = (canvasHeight - frameSize) / 2.2f

            // Dim outer region
            drawRect(color = Color(0x99000000))

            // Clear cutout
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameSize, frameSize),
                cornerRadius = CornerRadius(24f, 24f),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // Draw scanning laser beam
            val laserY = top + (frameSize * laserPosition)
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x00A855F7),
                        Color(0xFFA855F7),
                        Color(0xFF38BDF8),
                        Color(0xFFA855F7),
                        Color(0x00A855F7)
                    )
                ),
                start = Offset(left + 8f, laserY),
                end = Offset(left + frameSize - 8f, laserY),
                strokeWidth = 6f
            )
        }

        // Frame Border & Corner Accents
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-30).dp)
                .size(260.dp)
                .border(2.dp, Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8))), RoundedCornerShape(20.dp))
        )

        // Top instructions and Flashlight button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🎯 Align QR Code in frame",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            IconButton(
                onClick = {
                    torchEnabled = !torchEnabled
                    cameraControl?.enableTorch(torchEnabled)
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (torchEnabled) Color(0xFFF59E0B) else Color(0xCC0F172A))
            ) {
                Text(text = if (torchEnabled) "🔦" else "💡", fontSize = 18.sp)
            }
        }
    }
}
