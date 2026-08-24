package com.tarkeshstack.speakeasy.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records the user's own voice to a file in app-private storage, purely so it can be
 * played back later — this runs alongside the system SpeechRecognizer used elsewhere in
 * the app, which handles transcription on its own and is unaffected if this fails.
 *
 * Recordings never leave the device: no upload, no cloud storage. They live in
 * `filesDir/recordings/` and are deleted when their history entry is deleted or history
 * is cleared (see HistoryRepository).
 */
class AudioRecorderController(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Starts recording to a new file, returning its path, or null if this device won't
     *  allow it (e.g. the mic is already claimed exclusively) — callers should treat a
     *  null result as "no recording for this turn" and continue normally. */
    fun start(): String? {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        return try {
            val mr = newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = file
            file.absolutePath
        } catch (e: Exception) {
            release()
            null
        }
    }

    /** Stops the current recording and returns its file path, or null if nothing usable
     *  was captured. Safe to call even if [start] failed or was never called. */
    fun stop(): String? {
        return try {
            recorder?.stop()
            outputFile?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            release()
        }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun release() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (e: Exception) {
            // Already released or never fully started — nothing to clean up.
        }
        recorder = null
    }
}
