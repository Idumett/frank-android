package com.idumet.frank.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        check(recorder == null) {
            "Audio recording is already running"
        }

        val file = File(
            context.cacheDir,
            "frank_recording_${System.currentTimeMillis()}.m4a"
        )

        val mediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)

            prepare()
            start()
        }

        recorder = mediaRecorder
        outputFile = file

        return file
    }

    fun stop(): File? {
        val activeRecorder = recorder ?: return null
        val file = outputFile

        try {
            activeRecorder.stop()
        } catch (exception: RuntimeException) {
            file?.delete()
            throw IllegalStateException(
                "Recording was too short or failed",
                exception
            )
        } finally {
            activeRecorder.release()
            recorder = null
            outputFile = null
        }

        return file
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // A very short recording may fail when stopped.
        } finally {
            recorder?.release()
            recorder = null

            outputFile?.delete()
            outputFile = null
        }
    }

    fun isRecording(): Boolean = recorder != null
}