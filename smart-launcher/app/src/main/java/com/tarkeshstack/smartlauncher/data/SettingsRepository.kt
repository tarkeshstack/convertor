package com.tarkeshstack.smartlauncher.data

import android.content.Context

/** Small persisted app preferences — currently just whether the home screen shows its
 *  quick-access commands section. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun showCommandsOnHome(): Boolean = prefs.getBoolean(KEY_SHOW_COMMANDS_ON_HOME, true)

    fun setShowCommandsOnHome(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_COMMANDS_ON_HOME, show).apply()
    }

    private companion object {
        const val KEY_SHOW_COMMANDS_ON_HOME = "show_commands_on_home"
    }
}
