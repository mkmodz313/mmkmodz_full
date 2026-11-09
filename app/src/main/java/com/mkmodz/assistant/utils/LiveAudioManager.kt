package com.mkmodz.assistant.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Plays raw PCM 16-bit mono audio (24 kHz) coming from Gemini Live. */
class LiveAudioManager(private val context: Context) {

    private val sampleRate = 24000
    private var track: AudioTrack? = null
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private fun ensureTrack(): AudioTrack? {
        synchronized(lock) {
            if (track == null) {
                try {
                    val minBuf = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf * 4)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    t.play()
                    track = t
                } catch (e: Exception) {
                    Log.e("LiveAudioManager", "AudioTrack error: ${e.message}")
                }
            }
            return track
        }
    }

    fun playChunk(data: ByteArray) {
        if (data.isEmpty()) return
        try {
            if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    ensureTrack()?.write(data, 0, data.size)
                } catch (e: Exception) {
                    Log.e("LiveAudioManager", "write error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("LiveAudioManager", "playChunk error: ${e.message}")
        }
    }

    fun stop() {
        synchronized(lock) {
            try {
                track?.pause()
                track?.flush()
                track?.stop()
                track?.release()
            } catch (e: Exception) {
                Log.e("LiveAudioManager", "stop error: ${e.message}")
            }
            track = null
        }
    }
}
