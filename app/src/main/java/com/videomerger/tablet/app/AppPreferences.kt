package com.videomerger.tablet.app

import android.content.Context
import android.net.Uri
import com.videomerger.tablet.BuildConfig

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    init {
        val previousVersion = preferences.getInt(KEY_VERSION, -1)
        if (previousVersion != -1 && previousVersion != BuildConfig.VERSION_CODE) {
            preferences.getString(KEY_SOURCE_URI, null)?.let { saved ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(saved),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            preferences.edit().clear().putInt(KEY_VERSION, BuildConfig.VERSION_CODE).apply()
        } else if (previousVersion == -1) {
            preferences.edit().putInt(KEY_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }

    var sourceTreeUri: Uri?
        get() = preferences.getString(KEY_SOURCE_URI, null)?.let(Uri::parse)
        set(value) = preferences.edit().putString(KEY_SOURCE_URI, value?.toString()).apply()

    var darkTheme: Boolean
        get() = preferences.getBoolean(KEY_DARK_THEME, false)
        set(value) = preferences.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND, false)
        set(value) = preferences.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION, false)
        set(value) = preferences.edit().putBoolean(KEY_VIBRATION, value).apply()

    companion object {
        private const val KEY_VERSION = "version_code"
        private const val KEY_SOURCE_URI = "source_tree_uri"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
    }
}
