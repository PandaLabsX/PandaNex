package com.example.feature.qrbarcode

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.SectionHeader
import com.example.ui.theme.LocalAquaColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

private enum class QrTemplateType(val label: String) {
    TEXT("Text"),
    URL("URL"),
    WIFI("Wi-Fi"),
    CONTACT("Contact vCard")
}

// MODULE 14 — QR & BARCODE TOOLKIT
@Composable
fun QrBarcodeToolkitScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aqua = LocalAquaColors.current
    var mode by remember { mutableStateOf("GENERATE") } // GENERATE or SCAN

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            mode = "SCAN"
        } else {
            Toast.makeText(context, "Camera permission is required only when scanning QR/Barcodes", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "QR & Barcode Toolkit",
            subtitle = "Module 14 • ML Kit Scanner & ZXing QR Generator",
            onBack = onBack
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = mode == "GENERATE",
                onClick = { mode = "GENERATE" },
                label = { Text("Generate QR") },
                leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                modifier = Modifier.testTag("qr_mode_generate")
            )
            FilterChip(
                selected = mode == "SCAN",
                onClick = {
                    if (hasCameraPermission) {
                        mode = "SCAN"
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                label = { Text("Scan QR / Barcode") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                modifier = Modifier.testTag("qr_mode_scan")
            )
        }

        if (mode == "GENERATE") {
            QrGeneratorContent()
        } else {
            QrScannerContent(
                hasPermission = hasCameraPermission,
                onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Composable
private fun QrGeneratorContent() {
    val context = LocalContext.current
    var template by remember { mutableStateOf(QrTemplateType.TEXT) }

    var plainText by remember { mutableStateOf("PandaNex — Unlock More From Your Phone.") }
    var urlText by remember { mutableStateOf("https://developer.android.com") }
    var wifiSsid by remember { mutableStateOf("PandaNex_5G") }
    var wifiPassword by remember { mutableStateOf("AquaDynamics2026") }
    var contactName by remember { mutableStateOf("Alex Rivera") }
    var contactPhone by remember { mutableStateOf("+15550192834") }
    var contactEmail by remember { mutableStateOf("alex@pandanex.example") }

    val formattedPayload = remember(
        template, plainText, urlText, wifiSsid, wifiPassword, contactName, contactPhone, contactEmail
    ) {
        when (template) {
            QrTemplateType.TEXT -> plainText.ifBlank { "PandaNex" }
            QrTemplateType.URL -> urlText.ifBlank { "https://android.com" }
            QrTemplateType.WIFI -> "WIFI:T:WPA;S:$wifiSsid;P:$wifiPassword;;"
            QrTemplateType.CONTACT -> "BEGIN:VCARD\nVERSION:3.0\nFN:$contactName\nTEL:$contactPhone\nEMAIL:$contactEmail\nEND:VCARD"
        }
    }

    val qrBitmap = remember(formattedPayload) {
        generateQrBitmap(formattedPayload, 640)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AquaGlassCard(glowAccent = true) {
                SectionHeader(title = "QR Payload Type", icon = Icons.Default.QrCode2)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QrTemplateType.entries) { item ->
                        FilterChip(
                            selected = template == item,
                            onClick = { template = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                when (template) {
                    QrTemplateType.TEXT -> {
                        OutlinedTextField(
                            value = plainText,
                            onValueChange = { plainText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Text Content") }
                        )
                    }
                    QrTemplateType.URL -> {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Website URL") }
                        )
                    }
                    QrTemplateType.WIFI -> {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Wi-Fi Network Name (SSID)") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("WPA2/WPA3 Password") }
                        )
                    }
                    QrTemplateType.CONTACT -> {
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Full Name") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Phone Number") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contactEmail,
                            onValueChange = { contactEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email Address") }
                        )
                    }
                }
            }
        }

        if (qrBitmap != null) {
            item {
                AquaGlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Generated QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { saveQrToGallery(context, qrBitmap) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save PNG")
                            }
                            OutlinedButton(
                                onClick = { shareQrBitmap(context, qrBitmap, formattedPayload) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun QrScannerContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var scannedFormat by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasPermission) {
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Camera Permission Required", icon = Icons.Default.CameraAlt)
                    Text(
                        "PandaNex requests camera access strictly when scanning QR codes or barcodes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        } else {
            item {
                AquaGlassCard(glowAccent = true) {
                    SectionHeader(title = "Live CameraX + ML Kit Scanner", icon = Icons.Default.QrCodeScanner)
                    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
                    val barcodeScanner = remember { BarcodeScanning.getClient() }

                    DisposableEffect(Unit) {
                        onDispose {
                            barcodeScanner.close()
                            analyzerExecutor.shutdown()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val analysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val img = InputImage.fromMediaImage(
                                                    mediaImage,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                barcodeScanner.process(img)
                                                    .addOnSuccessListener { barcodes ->
                                                        val hit = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                                        if (hit != null && hit.rawValue != scannedValue) {
                                                            scannedValue = hit.rawValue
                                                            scannedFormat = "Format #${hit.format}"
                                                            SystemUtils.triggerHapticPulse(ctx, 45L)
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }
                }
            }
        }

        item {
            AquaGlassCard {
                SectionHeader(title = "Scan Result")
                if (scannedValue == null) {
                    Text(
                        "Point camera at any QR code, EAN, UPC, Code-128, or DataMatrix barcode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = scannedValue ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { SystemUtils.copyToClipboard(context, "Scanned Code", scannedValue ?: "") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = {
                                SystemUtils.shareTextReport(context, "Scanned QR/Barcode", scannedValue ?: "")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.rgb(6, 18, 36) else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

private fun saveQrToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "PandaNex_QR_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PandaNex")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(context, "Saved QR image to Pictures/PandaNex", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to save QR image", Toast.LENGTH_SHORT).show()
    }
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap, payload: String) {
    try {
        val dir = File(context.cacheDir, "qr").apply { mkdirs() }
        val file = File(dir, "pandanex_qr.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, payload)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR Image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        SystemUtils.shareTextReport(context, "QR Payload", payload)
    }
}
