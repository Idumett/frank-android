package com.idumet.frank.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FrankCamera(
    context: Context,
    private val captureIntervalMs: Long = 1_000L,
    private val jpegQuality: Int = 80,
    private val onFrameCaptured: ((File) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) {

    private val appContext =
        context.applicationContext

    private val mainExecutor =
        ContextCompat.getMainExecutor(appContext)

    private val cameraExecutor =
        Executors.newSingleThreadExecutor()

    private var captureScheduler:
            ScheduledExecutorService? = null

    private var cameraProvider:
            ProcessCameraProvider? = null

    private var imageCapture:
            ImageCapture? = null

    private var boundUseCase:
            UseCase? = null

    private val isStarted =
        AtomicBoolean(false)

    private val captureInProgress =
        AtomicBoolean(false)

    private val frameLock = Any()

    @Volatile
    private var latestFrameFile: File? = null

    val isRunning: Boolean
        get() = isStarted.get()

    /**
     * Starts the rear camera and captures a fresh JPEG frame approximately
     * once per second.
     *
     * Frames remain local until copyLatestFrame() is called.
     */
    fun start(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int =
            CameraSelector.LENS_FACING_BACK
    ) {
        if (!isStarted.compareAndSet(false, true)) {
            return
        }

        if (!hasCameraPermission()) {
            isStarted.set(false)

            reportError(
                "Camera permission has not been granted"
            )

            return
        }

        val providerFuture =
            ProcessCameraProvider.getInstance(
                appContext
            )

        providerFuture.addListener(
            {
                if (!isStarted.get()) {
                    return@addListener
                }

                try {
                    val provider =
                        providerFuture.get()

                    bindCamera(
                        provider = provider,
                        lifecycleOwner =
                            lifecycleOwner,
                        lensFacing = lensFacing
                    )
                } catch (exception: Exception) {
                    isStarted.set(false)

                    reportError(
                        exception.message
                            ?: "Unable to start camera"
                    )
                }
            },
            mainExecutor
        )
    }

    /**
     * Returns a safe copy of the newest captured image.
     *
     * A copy is returned because the camera may replace its internal latest
     * frame while the WebSocket client is reading the image.
     */
    fun copyLatestFrame(): File? {
        synchronized(frameLock) {
            val source =
                latestFrameFile
                    ?.takeIf {
                        it.exists() &&
                                it.length() > 0L
                    }
                    ?: return null

            return try {
                val copy =
                    File.createTempFile(
                        "frank-request-frame-",
                        ".jpg",
                        appContext.cacheDir
                    )

                source.copyTo(
                    target = copy,
                    overwrite = true
                )

                copy
            } catch (exception: Exception) {
                reportError(
                    exception.message
                        ?: "Unable to prepare camera frame"
                )

                null
            }
        }
    }

    /**
     * Stops frame capture and releases this class's CameraX use case.
     */
    fun stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return
        }

        stopCaptureScheduler()

        mainExecutor.execute {
            val provider = cameraProvider
            val useCase = boundUseCase

            if (
                provider != null &&
                useCase != null
            ) {
                try {
                    provider.unbind(useCase)
                } catch (_: Exception) {
                    // CameraX may already have unbound the use case.
                }
            }

            boundUseCase = null
            imageCapture = null
            cameraProvider = null
        }

        captureInProgress.set(false)
    }

    /**
     * Stops the camera and deletes its temporary frame.
     *
     * Call this when the assistant session is destroyed.
     */
    fun release() {
        stop()

        synchronized(frameLock) {
            latestFrameFile?.delete()
            latestFrameFile = null
        }

        cameraExecutor.shutdownNow()
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int
    ) {
        val selector =
            CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

        val captureUseCase =
            ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .setJpegQuality(
                    jpegQuality.coerceIn(
                        1,
                        100
                    )
                )
                .build()

        try {
            boundUseCase?.let {
                provider.unbind(it)
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                captureUseCase
            )

            cameraProvider = provider
            imageCapture = captureUseCase
            boundUseCase = captureUseCase

            startCaptureScheduler()
        } catch (exception: Exception) {
            isStarted.set(false)

            reportError(
                exception.message
                    ?: "Unable to bind camera"
            )
        }
    }

    private fun startCaptureScheduler() {
        stopCaptureScheduler()

        val scheduler =
            Executors.newSingleThreadScheduledExecutor()

        captureScheduler = scheduler

        scheduler.scheduleWithFixedDelay(
            {
                captureFrame()
            },
            0L,
            captureIntervalMs.coerceAtLeast(500L),
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopCaptureScheduler() {
        captureScheduler?.shutdownNow()
        captureScheduler = null
    }

    private fun captureFrame() {
        if (!isStarted.get()) {
            return
        }

        val capture =
            imageCapture
                ?: return

        if (
            !captureInProgress.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        val outputFile =
            try {
                File.createTempFile(
                    "frank-camera-frame-",
                    ".jpg",
                    appContext.cacheDir
                )
            } catch (exception: Exception) {
                captureInProgress.set(false)

                reportError(
                    exception.message
                        ?: "Unable to create camera frame"
                )

                return
            }

        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(outputFile)
                .build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {
                    captureInProgress.set(false)

                    if (!isStarted.get()) {
                        outputFile.delete()
                        return
                    }

                    if (
                        !outputFile.exists() ||
                        outputFile.length() == 0L
                    ) {
                        outputFile.delete()

                        reportError(
                            "Camera produced an empty frame"
                        )

                        return
                    }

                    replaceLatestFrame(
                        outputFile
                    )
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {
                    captureInProgress.set(false)
                    outputFile.delete()

                    if (isStarted.get()) {
                        reportError(
                            exception.message
                                ?: "Camera capture failed"
                        )
                    }
                }
            }
        )
    }

    private fun replaceLatestFrame(
        newFrame: File
    ) {
        synchronized(frameLock) {
            val previousFrame =
                latestFrameFile

            latestFrameFile = newFrame

            if (
                previousFrame != null &&
                previousFrame.absolutePath !=
                newFrame.absolutePath
            ) {
                previousFrame.delete()
            }
        }

        mainExecutor.execute {
            onFrameCaptured?.invoke(
                newFrame
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun reportError(
        message: String
    ) {
        mainExecutor.execute {
            onError?.invoke(message)
        }
    }
}