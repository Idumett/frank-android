package com.idumet.frank.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioRecorder(
    private val context: Context
) {

    companion object {
        const val SAMPLE_RATE = 16_000

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT

        private const val BITS_PER_SAMPLE = 16
        private const val CHANNEL_COUNT = 1
    }

    private val recording =
        AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private var pcmFile: File? = null
    private var wavFile: File? = null

    private var audioSampleListener:
            ((ShortArray, Int) -> Unit)? = null

    private var errorListener:
            ((String) -> Unit)? = null

    val isRecording: Boolean
        get() = recording.get()

    fun setAudioSampleListener(
        listener: ((ShortArray, Int) -> Unit)?
    ) {
        audioSampleListener = listener
    }

    fun setErrorListener(
        listener: ((String) -> Unit)?
    ) {
        errorListener = listener
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording.get()) {
            return
        }

        ensureMicrophonePermission()

        val minimumBufferSize =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

        if (minimumBufferSize <= 0) {
            throw IllegalStateException(
                "Unable to determine microphone buffer size"
            )
        }

        val bufferSize =
            minimumBufferSize * 2

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (
            recorder.state !=
            AudioRecord.STATE_INITIALIZED
        ) {
            recorder.release()

            throw IllegalStateException(
                "Unable to initialise microphone"
            )
        }

        val createdPcmFile = File.createTempFile(
            "frank-recording-",
            ".pcm",
            context.cacheDir
        )

        val createdWavFile = File(
            context.cacheDir,
            "frank-recording-${System.currentTimeMillis()}.wav"
        )

        audioRecord = recorder
        pcmFile = createdPcmFile
        wavFile = createdWavFile

        recording.set(true)
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone did not start recording"
            }
        } catch (exception: Exception) {
            cancel()
            throw exception
        }

        recordingThread = thread(
            name = "FrankAudioRecorder",
            start = true
        ) {
            recordAudio(
                recorder = recorder,
                outputFile = createdPcmFile,
                bufferSize = bufferSize
            )
        }
    }

    fun stop(): File? {
        if (!recording.compareAndSet(true, false)) {
            return null
        }

        stopAudioRecord()
        waitForRecordingThread()

        val sourcePcmFile = pcmFile
        val destinationWavFile = wavFile

        releaseRecorder()

        if (
            sourcePcmFile == null ||
            destinationWavFile == null ||
            !sourcePcmFile.exists() ||
            sourcePcmFile.length() == 0L
        ) {
            cleanupFiles()
            return null
        }

        writeWavFile(
            pcmFile = sourcePcmFile,
            wavFile = destinationWavFile
        )

        sourcePcmFile.delete()
        pcmFile = null
        wavFile = null

        return destinationWavFile
    }

    fun cancel() {
        recording.set(false)

        stopAudioRecord()
        waitForRecordingThread()
        releaseRecorder()
        cleanupFiles()
    }

    private fun recordAudio(
        recorder: AudioRecord,
        outputFile: File,
        bufferSize: Int
    ) {
        val shortBuffer =
            ShortArray(bufferSize / 2)

        try {
            FileOutputStream(outputFile).use { output ->
                while (recording.get()) {
                    val sampleCount = recorder.read(
                        shortBuffer,
                        0,
                        shortBuffer.size
                    )

                    if (!recording.get()) break
                    check(sampleCount >= 0) { "Microphone read failed (code $sampleCount)" }
                    if (sampleCount == 0) continue

                    writeShortSamples(
                        output = output,
                        samples = shortBuffer,
                        sampleCount = sampleCount
                    )

                    val listenerCopy =
                        shortBuffer.copyOf(sampleCount)

                    audioSampleListener?.invoke(
                        listenerCopy,
                        sampleCount
                    )
                }
            }
        } catch (exception: Exception) {
            if (recording.getAndSet(false)) {
                errorListener?.invoke(exception.message ?: "Audio recording failed")
            }
        }
    }

    private fun writeShortSamples(
        output: FileOutputStream,
        samples: ShortArray,
        sampleCount: Int
    ) {
        val byteBuffer =
            ByteArray(sampleCount * 2)

        var byteIndex = 0

        for (index in 0 until sampleCount) {
            val sample = samples[index].toInt()

            byteBuffer[byteIndex] =
                (sample and 0xFF).toByte()

            byteBuffer[byteIndex + 1] =
                ((sample shr 8) and 0xFF).toByte()

            byteIndex += 2
        }

        output.write(byteBuffer)
    }

    private fun stopAudioRecord() {
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Recorder may already be stopped.
        }
    }

    private fun waitForRecordingThread() {
        try {
            recordingThread?.join(1_500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        recordingThread = null
    }

    private fun releaseRecorder() {
        audioRecord?.release()
        audioRecord = null

        audioSampleListener = null
    }

    private fun cleanupFiles() {
        pcmFile?.delete()
        wavFile?.delete()

        pcmFile = null
        wavFile = null
    }

    private fun ensureMicrophonePermission() {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            throw SecurityException(
                "Microphone permission has not been granted"
            )
        }
    }

    private fun writeWavFile(
        pcmFile: File,
        wavFile: File
    ) {
        val pcmData = pcmFile.readBytes()
        val dataSize = pcmData.size
        val byteRate =
            SAMPLE_RATE *
                    CHANNEL_COUNT *
                    BITS_PER_SAMPLE / 8

        FileOutputStream(wavFile).use { output ->
            output.write(
                createWavHeader(
                    dataSize = dataSize,
                    byteRate = byteRate
                )
            )

            output.write(pcmData)
        }
    }

    private fun createWavHeader(
        dataSize: Int,
        byteRate: Int
    ): ByteArray {
        val header = ByteArray(44)

        writeAscii(header, 0, "RIFF")
        writeIntLittleEndian(
            header,
            4,
            36 + dataSize
        )

        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")

        writeIntLittleEndian(
            header,
            16,
            16
        )

        writeShortLittleEndian(
            header,
            20,
            1
        )

        writeShortLittleEndian(
            header,
            22,
            CHANNEL_COUNT
        )

        writeIntLittleEndian(
            header,
            24,
            SAMPLE_RATE
        )

        writeIntLittleEndian(
            header,
            28,
            byteRate
        )

        writeShortLittleEndian(
            header,
            32,
            CHANNEL_COUNT *
                    BITS_PER_SAMPLE / 8
        )

        writeShortLittleEndian(
            header,
            34,
            BITS_PER_SAMPLE
        )

        writeAscii(header, 36, "data")

        writeIntLittleEndian(
            header,
            40,
            dataSize
        )

        return header
    }

    private fun writeAscii(
        target: ByteArray,
        offset: Int,
        value: String
    ) {
        value.toByteArray(Charsets.US_ASCII)
            .copyInto(
                destination = target,
                destinationOffset = offset
            )
    }

    private fun writeIntLittleEndian(
        target: ByteArray,
        offset: Int,
        value: Int
    ) {
        target[offset] =
            (value and 0xFF).toByte()

        target[offset + 1] =
            ((value shr 8) and 0xFF).toByte()

        target[offset + 2] =
            ((value shr 16) and 0xFF).toByte()

        target[offset + 3] =
            ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLittleEndian(
        target: ByteArray,
        offset: Int,
        value: Int
    ) {
        target[offset] =
            (value and 0xFF).toByte()

        target[offset + 1] =
            ((value shr 8) and 0xFF).toByte()
    }
}
