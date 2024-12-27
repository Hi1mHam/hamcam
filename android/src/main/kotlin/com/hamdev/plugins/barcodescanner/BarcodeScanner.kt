package com.hamdev.plugins.barcodescanner

import android.Manifest
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CapacitorPlugin(
    name = "BarcodeScanner",
    permissions = [
        Permission(
            strings = [Manifest.permission.CAMERA],
            alias = "camera"
        )
    ]
)
class BarcodeScanner : Plugin() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var frameLayout: FrameLayout? = null
    private var cameraExecutor: ExecutorService? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var scanJob: Job? = null
    private var isScanning = false

    override fun load() {
        super.load()
        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .enableAllPotentialBarcodes()
                .build()
        )
    }

    @PluginMethod
    fun prepare(call: PluginCall) {
        try {
            setupUI()
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to prepare scanner: ${e.message}")
        }
    }

    @PluginMethod
    fun hideBackground(call: PluginCall) {
        try {
            activity.runOnUiThread {
                activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to hide background: ${e.message}")
        }
    }

    @PluginMethod
    fun showBackground(call: PluginCall) {
        try {
            activity.runOnUiThread {
                activity.window.decorView.setBackgroundColor(Color.WHITE)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to show background: ${e.message}")
        }
    }

    @PluginMethod
    fun startScan(call: PluginCall) {
        if (!hasRequiredPermissions()) {
            requestPermissions(call)
            return
        }

        try {
            isScanning = true
            startCamera(call)
        } catch (e: Exception) {
            call.reject("Failed to start scan: ${e.message}")
        }
    }

    @PluginMethod
    fun stopScan(call: PluginCall) {
        try {
            isScanning = false
            scanJob?.cancel()
            cameraProvider?.unbindAll()
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to stop scan: ${e.message}")
        }
    }

    private fun setupUI() {
        activity.runOnUiThread {
            frameLayout = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            previewView = PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            frameLayout?.addView(previewView)
            bridge.activity.addContentView(
                frameLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun startCamera(call: PluginCall) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView?.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                scanJob = CoroutineScope(Dispatchers.Default).launch {
                    processImage(imageProxy, call)
                }
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                call.reject("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun processImage(imageProxy: ImageProxy, call: PluginCall) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            try {
                val barcodes = withContext(Dispatchers.Default) {
                    barcodeScanner?.process(image)?.await()
                }

                barcodes?.firstOrNull()?.let { barcode ->
                    isScanning = false
                    withContext(Dispatchers.Main) {
                        call.resolve(
                            JSObject().apply {
                                put("hasContent", true)
                                put("content", barcode.rawValue ?: "")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Failed to process image: ${e.message}")
                }
            }
        }
        imageProxy.close()
    }

    @PermissionCallback
    private fun permissionCallback(call: PluginCall) {
        if (hasRequiredPermissions()) {
            startScan(call)
        } else {
            call.reject("Camera permission not granted")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return getPermissionState("camera") == PermissionState.GRANTED
    }

    private fun requestPermissions(call: PluginCall) {
        requestPermissionForAlias("camera", call, "permissionCallback")
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        cameraExecutor?.shutdown()
        barcodeScanner?.close()
    }
}
