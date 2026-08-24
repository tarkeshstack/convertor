package com.tarkeshstack.speakeasy.data

import android.content.Context

/** Stores the user's own Anthropic API key locally (app-private storage only —
 *  never bundled with the app or committed anywhere). Translation is powered by the
 *  Claude API, so this is required to use the app at all. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("speakeasy_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    companion object {
        private const val KEY_API_KEY = "anthropic_api_key"
    }
}
