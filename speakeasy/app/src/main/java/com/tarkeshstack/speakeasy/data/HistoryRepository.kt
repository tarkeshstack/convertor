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
        if (!file.exists()) return@withContext emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ConversationEntry(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                original = obj.getString("original"),
                corrected = obj.getString("corrected"),
                simplified = obj.optString("simplified", "").ifBlank { null },
                issueCount = obj.optInt("issueCount", 0),
            )
        }.sortedByDescending { it.timestamp }
    }

    suspend fun saveEntry(entry: ConversationEntry): List<ConversationEntry> = withContext(Dispatchers.IO) {
        val updated = listOf(entry) + loadAllUnsorted()
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun deleteEntry(id: String): List<ConversationEntry> = withContext(Dispatchers.IO) {
        val updated = loadAllUnsorted().filterNot { it.id == id }
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
        Unit
    }

    private fun loadAllUnsorted(): List<ConversationEntry> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ConversationEntry(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                original = obj.getString("original"),
                corrected = obj.getString("corrected"),
                simplified = obj.optString("simplified", "").ifBlank { null },
                issueCount = obj.optInt("issueCount", 0),
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
                },
            )
        }
        file.writeText(array.toString())
    }
}
