package com.tuandi.qrsanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QRAnalyzer constructor(private val qrCode: (Barcode, InputImage) -> Unit) :
    ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { image ->
            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()
            scanner.process(inputImage)
                .addOnCompleteListener {
                    imageProxy.close()
                    if (it.isSuccessful) {
                        for (barcode in it.result) {
                            when (barcode.format) {
                                Barcode.FORMAT_QR_CODE -> {
                                    qrCode(barcode, inputImage)
                                }
                            }
                        }
                    } else {
                        it.exception?.printStackTrace()
                    }
                }
        }
    }
}
