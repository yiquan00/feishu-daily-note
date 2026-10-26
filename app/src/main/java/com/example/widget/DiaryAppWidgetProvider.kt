package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.DiaryApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.DiaryDatabase
import com.example.data.local.DiaryEntry
import com.example.data.local.SyncStatus
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class DiaryAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (appWidgetIds.isNotEmpty()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    performUpdate(context, appWidgetManager, appWidgetIds)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onUpdate", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = UserPreferencesRepository(context)

        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, DiaryAppWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            performUpdate(context, appWidgetManager, appWidgetIds)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in ACTION_REFRESH_WIDGET", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_WIDGET_RANDOM -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val matching = getMatchingEntries(context, prefs.loadWidgetTag())
                        if (matching.isNotEmpty()) {
                            val current = prefs.loadWidgetIndex()
                            val newIndex = if (matching.size > 1) {
                                var rnd = Random.nextInt(matching.size)
                                while (rnd == current) {
                                    rnd = Random.nextInt(matching.size)
                                }
                                rnd
                            } else {
                                0
                            }
                            prefs.saveWidgetIndex(newIndex)
                        }
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val componentName = ComponentName(context, DiaryAppWidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                            performUpdate(context, appWidgetManager, appWidgetIds)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling random in widget", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun performUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = UserPreferencesRepository(context)
        val selectedTag = prefs.loadWidgetTag()
        val matching = getMatchingEntries(context, selectedTag)

        var currentIndex = prefs.loadWidgetIndex()
        if (matching.isNotEmpty() && currentIndex >= matching.size) {
            currentIndex = 0
            prefs.saveWidgetIndex(0)
        }

        val currentEntry: DiaryEntry? = if (matching.isNotEmpty()) {
            matching.getOrNull(currentIndex) ?: matching.firstOrNull()
        } else {
            null
        }

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_diary_layout)

            // 1. Tag indicator
            val tagDisplay = if (selectedTag == "全部") "全部" else selectedTag
            views.setTextViewText(R.id.widget_tag_badge, "轻·记 · $tagDisplay")

            // 2. Click Random / Shuffle Button (🎲 随机换一篇)
            val randomIntent = Intent(context, DiaryAppWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_RANDOM
            }
            val randomPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId * 10 + 1,
                randomIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_random, randomPendingIntent)

            // 3. Click Add Button (+ 写日记)
            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_EDITOR, true)
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                widgetId * 10 + 2,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_add, addPendingIntent)

            // 4. Click Entry Card opens Detail in App
            if (currentEntry != null) {
                views.setViewVisibility(R.id.widget_entry_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_DIARY_ID, currentEntry.id)
                }
                val mainPendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 3,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_entry_container, mainPendingIntent)

                val timeFormat = SimpleDateFormat("yyyy年M月d日 EEE HH:mm", Locale.CHINA)
                views.setTextViewText(R.id.widget_entry_date, timeFormat.format(Date(currentEntry.timestamp)))

                // Pure diary text snippet
                val previewText = currentEntry.content.ifBlank { "（无文字内容）" }
                views.setTextViewText(R.id.widget_entry_snippet, previewText)

                val firstTag = currentEntry.getTagList().firstOrNull() ?: "生活"
                val tagDisplayStr = if (currentEntry.location.isNotBlank()) {
                    "$firstTag · 📍${currentEntry.location}"
                } else {
                    firstTag
                }
                views.setTextViewText(R.id.widget_entry_tag, tagDisplayStr)

                val syncText = when (currentEntry.syncStatus) {
                    SyncStatus.SYNCED -> "● 飞书已同步"
                    SyncStatus.PENDING_UPLOAD, SyncStatus.PENDING_UPDATE -> "○ 待同步"
                    SyncStatus.ERROR -> "✕ 同步异常"
                    else -> "● 本地就绪"
                }
                views.setTextViewText(R.id.widget_entry_sync_status, syncText)
            } else {
                views.setViewVisibility(R.id.widget_entry_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_empty_text,
                    if (selectedTag == "全部") "暂无日记记录" else "「$selectedTag」下暂无日记"
                )
                views.setOnClickPendingIntent(R.id.widget_empty_container, addPendingIntent)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val TAG = "DiaryWidgetProvider"
        const val ACTION_REFRESH_WIDGET = "com.example.feishudiary.ACTION_REFRESH_WIDGET"
        const val ACTION_WIDGET_RANDOM = "com.example.feishudiary.ACTION_WIDGET_RANDOM"

        suspend fun getMatchingEntries(context: Context, tagFilter: String): List<DiaryEntry> {
            val db = DiaryDatabase.getDatabase(context)
            var all = db.diaryDao().getAllSynchronous()
            if (all.isEmpty()) {
                val repo = (context.applicationContext as? DiaryApplication)?.repository
                repo?.seedInitialDataIfEmpty()
                all = db.diaryDao().getAllSynchronous()
            }
            val distinct = all.distinctBy { it.id }.sortedByDescending { it.timestamp }
            return if (tagFilter == "全部") {
                distinct
            } else {
                distinct.filter { it.getTagList().contains(tagFilter) }
            }
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, DiaryAppWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, DiaryAppWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH_WIDGET
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast update", e)
            }
        }
    }
}
