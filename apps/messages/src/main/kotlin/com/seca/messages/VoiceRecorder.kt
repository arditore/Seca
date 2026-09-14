package com.seca.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

/**
 * Records a voice message: Opus in an Ogg file at a low bitrate, light enough
 * to travel through Seca Link, kept in the app's cache until it leaves.
 */
internal class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val elapsedMillis: Long get() = if (recorder == null) 0L else SystemClock.elapsedRealtime() - startedAt

    /** False without the microphone permission, or when the microphone is busy. */
    fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.ogg")
        val started = runCatching {
            recorder = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioChannels(1)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setMaxDuration(MAX_MILLIS.toInt())
                setOutputFile(target)
                prepare()
                start()
            }
        }.isSuccess
        if (started) {
            file = target
            startedAt = SystemClock.elapsedRealtime()
        } else {
            release()
            target.delete()
        }
        return started
    }

    /** Stops and hands over the recording, which the caller then owns; null when it failed or was too short. */
    fun stop(): File? {
        val length = elapsedMillis
        val stopped = runCatching { recorder?.stop() }.isSuccess
        release()
        val recorded = file
        file = null
        if (!stopped || length < MIN_MILLIS) {
            recorded?.delete()
            return null
        }
        return recorded
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        file?.delete()
        file = null
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }

    companion object {
        const val MAX_MILLIS = 5L * 60 * 1000
        private const val MIN_MILLIS = 700L
        private const val SAMPLE_RATE = 48_000
        private const val BIT_RATE = 24_000
    }
}
