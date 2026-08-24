package com.tarkeshstack.speakeasy.data

import android.content.Context
import com.tarkeshstack.speakeasy.model.InterpretationEntry
import com.tarkeshstack.speakeasy.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the interpretation history as a small JSON file in app-private storage. */
class HistoryRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "interpretation_history.json")

    suspend fun loadAll(): List<InterpretationEntry> = withContext(Dispatchers.IO) {
        loadAllUnsorted().sortedByDescending { it.timestamp }
    }

    suspend fun saveEntry(entry: InterpretationEntry): List<InterpretationEntry> = withContext(Dispatchers.IO) {
        val updated = listOf(entry) + loadAllUnsorted()
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun deleteEntry(id: String): List<InterpretationEntry> = withContext(Dispatchers.IO) {
        val updated = loadAllUnsorted().filterNot { it.id == id }
        writeAll(updated)
        updated.sortedByDescending { it.timestamp }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
        Unit
    }

    private fun loadAllUnsorted(): List<InterpretationEntry> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            val source = Language.entries.find { it.name == obj.optString("sourceLanguage") } ?: return@mapNotNull null
            val target = Language.entries.find { it.name == obj.optString("targetLanguage") } ?: return@mapNotNull null
            InterpretationEntry(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                originalText = obj.getString("originalText"),
                sourceLanguage = source,
                translatedText = obj.getString("translatedText"),
                targetLanguage = target,
            )
        }
    }

    private fun writeAll(entries: List<InterpretationEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("originalText", entry.originalText)
                    put("sourceLanguage", entry.sourceLanguage.name)
                    put("translatedText", entry.translatedText)
                    put("targetLanguage", entry.targetLanguage.name)
                },
            )
        }
        file.writeText(array.toString())
    }
}
