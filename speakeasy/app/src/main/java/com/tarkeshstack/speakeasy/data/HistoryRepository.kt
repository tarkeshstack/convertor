package com.tarkeshstack.speakeasy.data

import android.content.Context
import com.tarkeshstack.speakeasy.model.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the practice conversation history as a small JSON file in app-private storage. */
class HistoryRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "conversation_history.json")

    suspend fun loadAll(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        loadAllUnsorted().sortedByDescending { it.timestamp }
    }

    suspend fun saveEntry(entry: ConversationEntry): List<ConversationEntry> = withContext(Dispatchers.IO) {
        val updated = listOf(entry) + loadAllUnsorted()
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun deleteEntry(id: String): List<ConversationEntry> = withContext(Dispatchers.IO) {
        val all = loadAllUnsorted()
        all.firstOrNull { it.id == id }?.audioFilePath?.let { deleteAudioFile(it) }
        val updated = all.filterNot { it.id == id }
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        loadAllUnsorted().forEach { it.audioFilePath?.let { path -> deleteAudioFile(path) } }
        if (file.exists()) file.delete()
        Unit
    }

    private fun deleteAudioFile(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun loadAllUnsorted(): List<ConversationEntry> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val categories = obj.optJSONArray("issueCategories")
            ConversationEntry(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                original = obj.getString("original"),
                corrected = obj.getString("corrected"),
                simplified = obj.optString("simplified", "").ifBlank { null },
                issueCount = obj.optInt("issueCount", 0),
                issueCategories = categories?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                audioFilePath = obj.optString("audioFilePath", "").ifBlank { null },
            )
        }
    }

    private fun writeAll(entries: List<ConversationEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("original", entry.original)
                    put("corrected", entry.corrected)
                    put("simplified", entry.simplified ?: "")
                    put("issueCount", entry.issueCount)
                    put("issueCategories", JSONArray(entry.issueCategories))
                    put("audioFilePath", entry.audioFilePath ?: "")
                },
            )
        }
        file.writeText(array.toString())
    }
}
