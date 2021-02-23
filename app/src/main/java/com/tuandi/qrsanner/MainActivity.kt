package com.tuandi.qrsanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.util.DisplayMetrics
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tuandi.qrsanner.databinding.ActivityMainBinding
import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.Executors

const val PERMISSION_REQUEST_CAMERA = 0

class MainActivity : BaseActivity(), EasyPermissions.PermissionCallbacks {
    //Region MARK: - public fields
    //Endregion

    //Region MARK: - private fields
    private val binding: ActivityMainBinding by binding()
    private val cameraProviderFuture by lazy { ProcessCameraProvider.getInstance(this) }
    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }
    private var stopScanner = false
    private val rec2 by lazy {
        RectF(
            binding.img.left.toFloat(),
            binding.img.top.toFloat(),
            binding.img.right.toFloat(),
            binding.img.bottom.toFloat()
        )
    }
    private var scaleFactorX = 1.0f
    private var scaleFactorY = 1.0f
    //Endregion

    //Region MARK: - public methods
    override fun onResume() {
        super.onResume()
        // Check if the Camera permission has been granted
        if (checkSelfPermissionCompat(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Permission is missing and must be requested.
            requestCameraPermission()
        }
    }

    override fun getLayoutId(): Int = R.layout.activity_main

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        startCamera()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        MaterialDialog(this).show {
            title(R.string.app_name)
            message(R.string.camera_permission_denied)
            positiveButton(R.string.close) {
                requestCameraPermission()
            }
            negativeButton(R.string.grant) {
                this@MainActivity.finish()
            }
        }
    }

    //Endregion

    //Region MARK: - private methods
    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()
        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.previewView.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(
            cameraExecutor,
            QRAnalyzer { barcode: Barcode, inputImage: InputImage ->
                if (isPortraitMode()) {
                    scaleFactorY = binding.previewView.height.toFloat() / inputImage.width
                    scaleFactorX = binding.previewView.width.toFloat() / inputImage.height
                } else {
                    scaleFactorY = binding.previewView.height.toFloat() / inputImage.height
                    scaleFactorX = binding.previewView.width.toFloat() / inputImage.width
                }
                barcode.boundingBox?.let { rect ->
                    val qrCoreRect = translateRect(rect)
                    if (rec2.contains(qrCoreRect)) {
                        if (stopScanner.not()) {
                            stopScanner = true
                            cameraProvider.unbindAll()
                            MaterialDialog(this).show {
                                title(R.string.app_name)
                                message(text = barcode.rawValue?.toString())
                                positiveButton(R.string.close) {
                                    stopScanner = false
                                    cameraProvider.bindToLifecycle(
                                        this@MainActivity as LifecycleOwner,
                                        cameraSelector,
                                        imageAnalysis,
                                        preview
                                    )
                                }
                            }
                        }
                    }
                }
            })
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    private fun requestCameraPermission() {
        EasyPermissions.requestPermissions(
            this, getString(R.string.camera_access_required),
            PERMISSION_REQUEST_CAMERA, Manifest.permission.CAMERA
        )
    }

    private fun translateX(x: Float): Float = x * scaleFactorX
    private fun translateY(y: Float): Float = y * scaleFactorY
    private fun translateRect(rect: Rect) = RectF(
        translateX(rect.left.toFloat()),
        translateY(rect.top.toFloat()),
        translateX(rect.right.toFloat()),
        translateY(rect.bottom.toFloat())
    )

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }
    //Endregion
}
