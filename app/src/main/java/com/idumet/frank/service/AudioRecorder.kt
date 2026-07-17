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
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(
    private val context: Context
) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    private val recording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(): File {
        check(!recording.get()) {
            "Audio recording is already running"
        }

        check(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "Microphone permission has not been granted"
        }

        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        check(minimumBufferSize > 0) {
            "This device does not support the required audio format"
        }

        val bufferSize = minimumBufferSize * 2

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Failed to initialise microphone"
        }

        val file = File(
            context.cacheDir,
            "frank_recording_${System.currentTimeMillis()}.wav"
        )

        val randomAccessFile = RandomAccessFile(file, "rw").apply {
            setLength(0)
            write(ByteArray(WAV_HEADER_SIZE))
        }

        audioRecord = recorder
        outputFile = file
        recording.set(true)

        recorder.startRecording()

        recordingThread = Thread {
            writeAudioData(
                recorder = recorder,
                output = randomAccessFile,
                bufferSize = bufferSize
            )
        }.apply {
            name = "FrankAudioRecorder"
            start()
        }

        return file
    }

    fun stop(): File? {
        if (!recording.compareAndSet(true, false)) {
            return null
        }

        val recorder = audioRecord
        val file = outputFile

        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // Recorder may already have stopped after an audio error.
        }

        recordingThread?.join()

        recorder?.release()

        audioRecord = null
        recordingThread = null
        outputFile = null

        if (file == null || file.length() <= WAV_HEADER_SIZE) {
            file?.delete()
            throw IllegalStateException("No audio was recorded")
        }

        return file
    }

    fun cancel() {
        if (recording.compareAndSet(true, false)) {
            try {
                audioRecord?.stop()
            } catch (_: IllegalStateException) {
                // Ignore cleanup errors.
            }
        }

        recordingThread?.join()

        audioRecord?.release()
        audioRecord = null
        recordingThread = null

        outputFile?.delete()
        outputFile = null
    }

    fun isRecording(): Boolean = recording.get()

    private fun writeAudioData(
        recorder: AudioRecord,
        output: RandomAccessFile,
        bufferSize: Int
    ) {
        val samples = ShortArray(bufferSize / 2)
        var audioBytesWritten = 0L

        try {
            while (recording.get()) {
                val samplesRead = recorder.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING
                )

                if (samplesRead <= 0) {
                    continue
                }

                for (index in 0 until samplesRead) {
                    val sample = samples[index].toInt()

                    // WAV PCM data is little-endian.
                    output.write(sample and 0xFF)
                    output.write(sample shr 8 and 0xFF)
                }

                audioBytesWritten += samplesRead * BYTES_PER_SAMPLE
            }

            writeWavHeader(
                output = output,
                audioDataSize = audioBytesWritten
            )
        } finally {
            output.close()
        }
    }

    private fun writeWavHeader(
        output: RandomAccessFile,
        audioDataSize: Long
    ) {
        val byteRate =
            SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8

        val totalDataSize = audioDataSize + 36

        output.seek(0)

        output.writeBytes("RIFF")
        writeLittleEndianInt(output, totalDataSize.toInt())
        output.writeBytes("WAVE")

        output.writeBytes("fmt ")
        writeLittleEndianInt(output, 16)
        writeLittleEndianShort(output, 1) // PCM format
        writeLittleEndianShort(output, CHANNEL_COUNT)
        writeLittleEndianInt(output, SAMPLE_RATE)
        writeLittleEndianInt(output, byteRate)

        val blockAlign =
            CHANNEL_COUNT * BITS_PER_SAMPLE / 8

        writeLittleEndianShort(output, blockAlign)
        writeLittleEndianShort(output, BITS_PER_SAMPLE)

        output.writeBytes("data")
        writeLittleEndianInt(output, audioDataSize.toInt())
    }

    private fun writeLittleEndianInt(
        output: RandomAccessFile,
        value: Int
    ) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
        output.write(value shr 16 and 0xFF)
        output.write(value shr 24 and 0xFF)
    }

    private fun writeLittleEndianShort(
        output: RandomAccessFile,
        value: Int
    ) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = 2
        private const val WAV_HEADER_SIZE = 44

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT
    }
}