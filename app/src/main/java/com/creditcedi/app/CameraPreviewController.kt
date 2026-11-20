package com.creditcedi.app

import android.content.Context
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.io.File
import java.util.concurrent.Executor

internal class CameraPreviewController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val lensFacing: Int,
    private val onPreviewStateChanged: (Boolean) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewStreamState: LiveData<PreviewView.StreamState>? = null
    private var previewStreamObserver: Observer<PreviewView.StreamState>? = null
    private var closed = false

    fun start(target: FrameLayout) {
        if (closed || previewView != null) return
        val viewFinder = PreviewView(target.context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            scaleX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
            contentDescription = null
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        previewView = viewFinder
        target.addView(viewFinder, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                if (closed || previewView !== viewFinder || viewFinder.parent !== target) return@addListener

                val rotation = viewFinder.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build()

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    capture
                )
                cameraProvider = provider
                previewUseCase = preview
                imageCapture = capture
                val streamState = viewFinder.previewStreamState
                val observer = Observer<PreviewView.StreamState> { state ->
                    if (!closed && previewView === viewFinder) {
                        onPreviewStateChanged(state == PreviewView.StreamState.STREAMING)
                    }
                }
                previewStreamState = streamState
                previewStreamObserver = observer
                streamState.observe(lifecycleOwner, observer)
            } catch (failure: Exception) {
                if (!closed) onError(failure)
            }
        }, mainExecutor)
    }

    fun capture(output: File, onSaved: () -> Unit, onFailure: (Throwable) -> Unit) {
        val capture = imageCapture
        if (closed || capture == null) {
            onFailure(IllegalStateException("Camera preview is not ready."))
            return
        }

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val options = ImageCapture.OutputFileOptions.Builder(output)
            .setMetadata(metadata)
            .build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = onSaved()

            override fun onError(exception: ImageCaptureException) = onFailure(exception)
        })
    }

    fun close() {
        if (closed) return
        closed = true
        previewStreamObserver?.let { observer -> previewStreamState?.removeObserver(observer) }
        val provider = cameraProvider
        val useCases = listOfNotNull(previewUseCase, imageCapture)
        if (provider != null && useCases.isNotEmpty()) {
            runCatching { provider.unbind(*useCases.toTypedArray<UseCase>()) }
        }
        (previewView?.parent as? ViewGroup)?.removeView(previewView)
        cameraProvider = null
        previewUseCase = null
        imageCapture = null
        previewStreamState = null
        previewStreamObserver = null
        previewView = null
    }
}
