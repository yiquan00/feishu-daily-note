package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.remote.FeishuConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "feishu_diary_prefs",
        Context.MODE_PRIVATE
    )

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<FeishuConfig> = _configFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(loadThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _lastSyncTimeFlow = MutableStateFlow(loadLastSyncTime())
    val lastSyncTimeFlow: StateFlow<Long> = _lastSyncTimeFlow.asStateFlow()

    private val _widgetTagFlow = MutableStateFlow(loadWidgetTag())
    val widgetTagFlow: StateFlow<String> = _widgetTagFlow.asStateFlow()

    private val _widgetRandomModeFlow = MutableStateFlow(loadWidgetRandomMode())
    val widgetRandomModeFlow: StateFlow<Boolean> = _widgetRandomModeFlow.asStateFlow()

    private val _appLockEnabledFlow = MutableStateFlow(loadAppLockEnabled())
    val appLockEnabledFlow: StateFlow<Boolean> = _appLockEnabledFlow.asStateFlow()

    private val _editorFontSizeFlow = MutableStateFlow(loadEditorFontSize())
    val editorFontSizeFlow: StateFlow<Int> = _editorFontSizeFlow.asStateFlow()

    fun loadConfig(): FeishuConfig {
        return FeishuConfig(
            appId = prefs.getString(KEY_APP_ID, "") ?: "",
            appSecret = prefs.getString(KEY_APP_SECRET, "") ?: "",
            appToken = prefs.getString(KEY_APP_TOKEN, "") ?: "",
            tableId = prefs.getString(KEY_TABLE_ID, "") ?: "",
            autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true)
        )
    }

    fun saveConfig(config: FeishuConfig) {
        prefs.edit()
            .putString(KEY_APP_ID, config.appId.trim())
            .putString(KEY_APP_SECRET, config.appSecret.trim())
            .putString(KEY_APP_TOKEN, config.appToken.trim())
            .putString(KEY_TABLE_ID, config.tableId.trim())
            .putBoolean(KEY_AUTO_SYNC, config.autoSync)
            .apply()
        _configFlow.value = config
    }

    fun loadThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    fun loadLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun updateLastSyncTime(time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
        _lastSyncTimeFlow.value = time
    }

    fun loadWidgetTag(): String {
        return prefs.getString(KEY_WIDGET_TAG, "全部") ?: "全部"
    }

    fun saveWidgetTag(tag: String) {
        prefs.edit().putString(KEY_WIDGET_TAG, tag).apply()
        _widgetTagFlow.value = tag
    }

    fun loadWidgetRandomMode(): Boolean {
        return prefs.getBoolean(KEY_WIDGET_RANDOM_MODE, false)
    }

    fun saveWidgetRandomMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_RANDOM_MODE, enabled).apply()
        _widgetRandomModeFlow.value = enabled
    }

    fun loadWidgetIndex(): Int {
        return prefs.getInt(KEY_WIDGET_INDEX, 0)
    }

    fun saveWidgetIndex(index: Int) {
        prefs.edit().putInt(KEY_WIDGET_INDEX, index).apply()
    }

    fun loadAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun saveAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _appLockEnabledFlow.value = enabled
    }

    fun loadEditorFontSize(): Int {
        return prefs.getInt(KEY_EDITOR_FONT_SIZE, 18)
    }

    fun saveEditorFontSize(size: Int) {
        val validSize = when {
            size < 14 -> 14
            size > 26 -> 26
            else -> size
        }
        prefs.edit().putInt(KEY_EDITOR_FONT_SIZE, validSize).apply()
        _editorFontSizeFlow.value = validSize
    }

    companion object {
        const val KEY_APP_ID = "feishu_app_id"
        const val KEY_APP_SECRET = "feishu_app_secret"
        const val KEY_APP_TOKEN = "feishu_app_token"
        const val KEY_TABLE_ID = "feishu_table_id"
        const val KEY_AUTO_SYNC = "feishu_auto_sync"
        const val KEY_THEME_MODE = "app_theme_mode"
        const val KEY_LAST_SYNC_TIME = "last_sync_time"

        const val KEY_WIDGET_TAG = "widget_selected_tag"
        const val KEY_WIDGET_RANDOM_MODE = "widget_random_mode"
        const val KEY_WIDGET_INDEX = "widget_current_index"

        const val KEY_APP_LOCK_ENABLED = "system_lock_enabled"
        const val KEY_EDITOR_FONT_SIZE = "editor_font_size"

        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
    }
}
