package com.example

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DiaryEntry
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.FeishuConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("飞书日记", appName)
    }

    @Test
    fun `feishu config validation`() {
        val emptyConfig = FeishuConfig()
        assertFalse(emptyConfig.isComplete)

        val fullConfig = FeishuConfig(
            appId = "cli_123",
            appSecret = "sec_456",
            appToken = "bascn_789",
            tableId = "tbl_001"
        )
        assertTrue(fullConfig.isComplete)
    }

    @Test
    fun `widget preferences configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)

        assertEquals("全部", prefs.loadWidgetTag())
        prefs.saveWidgetTag("生活")
        assertEquals("生活", prefs.loadWidgetTag())

        assertFalse(prefs.loadWidgetRandomMode())
        prefs.saveWidgetRandomMode(true)
        assertTrue(prefs.loadWidgetRandomMode())
    }

    @Test
    fun `diary entry helper methods`() {
        val entry = DiaryEntry(
            title = "",
            content = "很棒的一天，记录点滴",
            tags = "生活, 工作",
            location = "朝阳大悦城"
        )
        assertEquals(2, entry.getTagList().size)
        assertTrue(entry.getTagList().contains("生活"))
        assertEquals("朝阳大悦城", entry.location)
    }

    @Test
    fun `widget layout remote views inflation succeeds without forbidden views`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_diary_layout)
        val parent = FrameLayout(context)
        val inflatedView = remoteViews.apply(context, parent)
        assertNotNull(inflatedView)
    }

    @Test
    fun `system app lock preferences validation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)

        // Default state: lock disabled
        assertFalse(prefs.loadAppLockEnabled())

        // Enable system lock
        prefs.saveAppLockEnabled(true)
        assertTrue(prefs.loadAppLockEnabled())

        // Disable system lock
        prefs.saveAppLockEnabled(false)
        assertFalse(prefs.loadAppLockEnabled())
    }
}
