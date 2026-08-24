package com.tarkeshstack.speakeasy.voice

import android.media.MediaPlayer
import android.util.Log
import java.io.File

private const val TAG = "AudioPlayback"

/** Plays back a user's own recorded voice clip (see [AudioRecorderController]) from a
 *  local file path. Purely local playback — nothing here touches the network. */
class AudioPlaybackController {

    private var player: MediaPlayer? = null

    /** [onResult] always fires with whether playback actually started and completed —
     *  callers should surface a `false` result to the user instead of failing silently,
     *  since a silent no-op here is indistinguishable from the app doing nothing at all. */
    fun play(filePath: String, onResult: (Boolean) -> Unit = {}) {
        stop()
        if (!File(filePath).exists()) {
            Log.w(TAG, "Recording file missing: $filePath")
            onResult(false)
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    release()
                    onResult(true)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Playback error for $filePath: what=$what extra=$extra")
                    release()
                    onResult(false)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't play recording: $filePath", e)
            release()
            onResult(false)
        }
    }

    fun stop() {
        release()
    }

    private fun release() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // Not started, or already stopped — nothing to do.
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }
}
