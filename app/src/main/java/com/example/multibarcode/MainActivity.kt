package com.example.multibarcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multibarcode.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val adapter = BarcodeResultAdapter()

    private var analyzer: BarcodeAnalyzer? = null
    private var camera: androidx.camera.core.Camera? = null
    private var torchOn = false

    /** Codes seen within the last [STABILIZE_WINDOW_MS], keyed by decoded value to de-duplicate. */
    private val tracked = LinkedHashMap<String, TrackedCode>()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = adapter
        binding.resultsList.itemAnimator = null

        binding.torchButton.setOnClickListener { toggleTorch() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                // Only ever process the most recent frame; old frames are dropped.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val barcodeAnalyzer = BarcodeAnalyzer { frame -> onFrame(frame) }
            analyzer = barcodeAnalyzer
            analysis.setAnalyzer(cameraExecutor, barcodeAnalyzer)

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Called on the analysis thread for every processed frame. */
    private fun onFrame(frame: BarcodeFrame) {
        val now = SystemClock.uptimeMillis()

        synchronized(tracked) {
            frame.barcodes.forEach { code ->
                tracked[code.value] = TrackedCode(code, now)
            }
            // Forget codes that have not been seen recently so the list stays in sync with reality.
            val iterator = tracked.entries.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().value.lastSeen > STABILIZE_WINDOW_MS) {
                    iterator.remove()
                }
            }
        }

        val stable: List<DetectedBarcode>
        synchronized(tracked) {
            stable = tracked.values.map { it.code }
        }

        val stableFrame = BarcodeFrame(stable, frame.imageWidth, frame.imageHeight)
        binding.overlayView.setResults(stableFrame)

        binding.overlayView.post { renderList(stable) }
    }

    private fun renderList(codes: List<DetectedBarcode>) {
        binding.countText.text = if (codes.isEmpty()) {
            getString(R.string.no_codes)
        } else {
            getString(R.string.detected_count, codes.size)
        }
        adapter.submit(codes)
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            torchOn = !torchOn
            cam.cameraControl.enableTorch(torchOn)
        } else {
            Toast.makeText(this, "No torch on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDenied() {
        Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show()
        binding.countText.text = getString(R.string.permission_rationale)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        analyzer?.close()
    }

    private data class TrackedCode(val code: DetectedBarcode, val lastSeen: Long)

    companion object {
        private const val TAG = "MultiBarcodeReader"
        /** A code lingers on screen for this long after its last detection, smoothing out flicker. */
        private const val STABILIZE_WINDOW_MS = 600L
    }
}
