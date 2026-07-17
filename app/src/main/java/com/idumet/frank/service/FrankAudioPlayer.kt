package com.idumet.frank.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import java.io.File

class FrankAudioPlayer(
    private val context: Context
) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    fun play(
        bytesBase64: String,
        onStarted: (() -> Unit)? = null,
        onCompleted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        stop()

        try {
            val audioBytes = Base64.decode(
                bytesBase64,
                Base64.DEFAULT
            )

            check(audioBytes.isNotEmpty()) {
                "Frank returned empty audio"
            }

            val file = File(
                context.cacheDir,
                "frank_reply_${System.currentTimeMillis()}.mp3"
            )

            file.writeBytes(audioBytes)
            currentFile = file

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                setDataSource(file.absolutePath)

                setOnPreparedListener {
                    onStarted?.invoke()
                    it.start()
                }

                setOnCompletionListener {
                    releasePlayer()
                    onCompleted?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    releasePlayer()

                    onError?.invoke(
                        "Audio playback failed: $what/$extra"
                    )

                    true
                }

                prepareAsync()
            }

            mediaPlayer = player
        } catch (exception: Exception) {
            releasePlayer()

            onError?.invoke(
                exception.message ?: "Failed to play Frank audio"
            )
        }
    }

    fun stop() {
        val player = mediaPlayer

        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: IllegalStateException) {
                // Player may not have reached the prepared state.
            }
        }

        releasePlayer()
    }

    fun release() {
        stop()
    }

    private fun releasePlayer() {
        mediaPlayer?.reset()
        mediaPlayer?.release()
        mediaPlayer = null

        currentFile?.delete()
        currentFile = null
    }
}