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

    /**
     * False without the microphone permission, or when the microphone is busy.
     *
     * [carrier] picks the format. Opus in an Ogg file is far better sound for the bytes, but a
     * multimedia message is read by whatever the other phone happens to be, and AMR in a 3GPP
     * file is the one every telephone has understood since before smartphones. Over Seca Link
     * both ends are ours, so the better format wins; over a carrier, the older one does.
     */
    fun start(carrier: Boolean = false): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.${if (carrier) "3gp" else "ogg"}")
        val started = runCatching {
            recorder = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (carrier) {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setAudioSamplingRate(AMR_SAMPLE_RATE)
                    setAudioEncodingBitRate(AMR_BIT_RATE)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setAudioSamplingRate(SAMPLE_RATE)
                    setAudioEncodingBitRate(BIT_RATE)
                }
                setAudioChannels(1)
                setMaxDuration(maxMillis(carrier).toInt())
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

        /**
         * How long one may speak for.
         *
         * Over a carrier the limit is not taste but arithmetic: AMR writes about a kilobyte and a
         * half a second, and a message past the size a network carries is refused whole. Stopping
         * at a length that will actually send beats recording three minutes that bounce.
         */
        fun maxMillis(carrier: Boolean): Long = if (carrier) MAX_CARRIER_MILLIS else MAX_MILLIS

        const val MAX_MILLIS = 5L * 60 * 1000
        private const val MAX_CARRIER_MILLIS = 2L * 60 * 1000
        private const val MIN_MILLIS = 700L
        private const val SAMPLE_RATE = 48_000
        private const val BIT_RATE = 24_000
        private const val AMR_SAMPLE_RATE = 8_000
        private const val AMR_BIT_RATE = 12_200
    }
}
