package com.tarkeshstack.smartlauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Persists how many times each app has been launched from this launcher, as a small
 *  JSON file in app-private storage — used to rank frequently used apps ahead of ones
 *  that have never been opened here. */
class AppUsageRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "app_usage.json")

    suspend fun loadAll(): Map<String, Int> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        val obj = JSONObject(file.readText())
        obj.keys().asSequence().associateWith { obj.getInt(it) }
    }

    suspend fun recordLaunch(packageName: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val obj = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        obj.put(packageName, obj.optInt(packageName, 0) + 1)
        file.writeText(obj.toString())
        obj.keys().asSequence().associateWith { obj.getInt(it) }
    }
}
