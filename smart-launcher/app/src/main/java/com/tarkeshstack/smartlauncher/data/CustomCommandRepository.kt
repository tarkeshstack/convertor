package com.tarkeshstack.smartlauncher.data

import android.content.Context
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists user-defined commands as a small JSON file in app-private storage. */
class CustomCommandRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "custom_commands.json")

    suspend fun loadAll(): List<CustomCommand> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CustomCommand(
                id = obj.getString("id"),
                phrase = obj.getString("phrase"),
                label = obj.getString("label"),
                kind = CustomCommandKind.valueOf(obj.getString("kind")),
                packageName = obj.optString("packageName", "").ifBlank { null },
                deepLinkUri = obj.optString("deepLinkUri", "").ifBlank { null },
                systemAction = obj.optString("systemAction", "").ifBlank { null },
            )
        }
    }

    suspend fun saveAll(commands: List<CustomCommand>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        commands.forEach { command ->
            array.put(
                JSONObject().apply {
                    put("id", command.id)
                    put("phrase", command.phrase)
                    put("label", command.label)
                    put("kind", command.kind.name)
                    put("packageName", command.packageName ?: "")
                    put("deepLinkUri", command.deepLinkUri ?: "")
                    put("systemAction", command.systemAction ?: "")
                },
            )
        }
        file.writeText(array.toString())
    }

    /** Exact phrase match wins; otherwise the first command whose phrase and the typed
     *  text contain one another (either direction), so a full sentence still matches. */
    fun findMatch(commands: List<CustomCommand>, text: String): CustomCommand? {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return null
        commands.firstOrNull { it.phrase.trim().lowercase() == needle }?.let { return it }
        return commands.firstOrNull { command ->
            val phrase = command.phrase.trim().lowercase()
            phrase.isNotEmpty() && (needle.contains(phrase) || phrase.contains(needle))
        }
    }
}
