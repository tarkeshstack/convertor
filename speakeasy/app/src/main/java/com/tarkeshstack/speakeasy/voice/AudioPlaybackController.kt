package com.tarkeshstack.speakeasy.voice

import android.media.MediaPlayer
import java.io.File

/** Plays back a user's own recorded voice clip (see [AudioRecorderController]) from a
 *  local file path. Purely local playback — nothing here touches the network. */
class AudioPlaybackController {

    private var player: MediaPlayer? = null

    /** [onDone] always fires, whether playback succeeded, failed, or the file is
     *  missing/unplayable — callers can safely act on it. */
    fun play(filePath: String, onDone: () -> Unit = {}) {
        stop()
        if (!File(filePath).exists()) {
            onDone()
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    release()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    onDone()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            release()
            onDone()
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
