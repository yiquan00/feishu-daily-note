package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.BlockingSyncState
import com.example.ui.DiaryViewModel
import com.example.ui.DiaryViewModelFactory
import com.example.ui.Screen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.DiaryDetailScreen
import com.example.ui.screens.DiaryEditorScreen
import com.example.ui.screens.DiaryFeedScreen
import com.example.ui.screens.FeishuSettingsScreen
import com.example.ui.theme.FeishuDiaryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DiaryViewModel by viewModels {
        DiaryViewModelFactory(
            (application as DiaryApplication).repository,
            applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                UserPreferencesRepository.THEME_DARK -> true
                UserPreferencesRepository.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            FeishuDiaryTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiaryApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_EDITOR, false)) {
            viewModel.navigateTo(Screen.EDITOR, null)
        } else {
            val diaryId = intent.getStringExtra(EXTRA_DIARY_ID)
            if (diaryId != null) {
                viewModel.navigateTo(Screen.DETAIL, diaryId)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_EDITOR = "extra_open_editor"
        const val EXTRA_DIARY_ID = "extra_diary_id"
    }
}

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val feishuConfig by viewModel.feishuConfig.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val widgetTag by viewModel.widgetTag.collectAsStateWithLifecycle()
    val widgetRandomMode by viewModel.widgetRandomMode.collectAsStateWithLifecycle()
    val blockingSyncState by viewModel.blockingSyncState.collectAsStateWithLifecycle()

    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState.currentScreen) {
            Screen.FEED -> {
                DiaryFeedScreen(
                    entries = entries,
                    allEntries = allEntries,
                    uiState = uiState,
                    feishuConfig = feishuConfig,
                    searchQuery = searchQuery,
                    selectedTag = selectedTag,
                    allTags = allTags,
                    onSearchChange = viewModel::setSearchQuery,
                    onTagSelect = viewModel::selectTagFilter,
                    onNavigate = viewModel::navigateTo,
                    onSyncAll = { viewModel.syncAll() },
                    onPullRefresh = { viewModel.refreshSync() },
                    onDeleteEntry = { viewModel.deleteDiary(it) },
                    onClearSyncMessage = viewModel::clearSyncMessage
                )
            }

            Screen.EDITOR -> {
                val activeEntry = allEntries.find { it.id == uiState.activeDiaryId } ?: entries.find { it.id == uiState.activeDiaryId }
                val editorFontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
                DiaryEditorScreen(
                    initialEntry = activeEntry,
                    fontSizeSp = editorFontSize,
                    onFontSizeChange = viewModel::setEditorFontSize,
                    onSave = { id, content, timestamp, tags, location, images ->
                        viewModel.saveDiary(
                            id = id,
                            content = content,
                            timestamp = timestamp,
                            tags = tags,
                            location = location,
                            images = images,
                            onSuccess = {
                                // Diary saved successfully
                            }
                        )
                    },
                    onBack = viewModel::navigateBack
                )
            }

            Screen.DETAIL -> {
                val activeEntry = allEntries.find { it.id == uiState.activeDiaryId } ?: entries.find { it.id == uiState.activeDiaryId }
                DiaryDetailScreen(
                    entry = activeEntry,
                    isSyncing = uiState.isSyncing,
                    syncMessage = uiState.syncMessage,
                    onClearSyncMessage = viewModel::clearSyncMessage,
                    onPullRefresh = { viewModel.refreshSync() },
                    onBack = viewModel::navigateBack,
                    onEdit = { entry ->
                        viewModel.navigateTo(Screen.EDITOR, entry.id)
                    },
                    onDelete = { entry ->
                        viewModel.deleteDiary(entry)
                    }
                )
            }

            Screen.FEISHU_SETTINGS -> {
                FeishuSettingsScreen(
                    currentConfig = feishuConfig,
                    themeMode = themeMode,
                    lastSyncTime = lastSyncTime,
                    widgetTag = widgetTag,
                    widgetRandomMode = widgetRandomMode,
                    isTesting = uiState.isTestingConnection,
                    testResult = uiState.testConnectionResult,
                    isSyncing = uiState.isSyncing,
                    appLockEnabled = appLockEnabled,
                    onToggleAppLock = viewModel::setAppLockEnabled,
                    onSaveConfig = viewModel::saveFeishuConfig,
                    onTestConnection = viewModel::testFeishuConnection,
                    onSyncAll = { viewModel.syncAll() },
                    onThemeChange = viewModel::setThemeMode,
                    onWidgetTagChange = viewModel::setWidgetTag,
                    onWidgetRandomModeChange = viewModel::setWidgetRandomMode,
                    onBack = viewModel::navigateBack
                )
            }
        }

        // 每次增删改查同步飞书时的半屏浮窗 (从底部上滑 ModalBottomSheet)
        if (blockingSyncState.isVisible) {
            BlockingSyncModal(
                state = blockingSyncState,
                onDismiss = viewModel::dismissBlockingSync,
                onGoToSettings = {
                    viewModel.dismissBlockingSync()
                    viewModel.navigateTo(Screen.FEISHU_SETTINGS)
                }
            )
        }

        // 系统自带密码锁 (如果开启且尚未解锁)
        if (appLockEnabled && !isUnlocked) {
            AppLockScreen(
                onUnlockSuccess = viewModel::unlock
            )
        }
    }
}

/**
 * 阻断操作半屏浮窗：
 * 当进行增删改查时，从底部上滑展示飞书多维表格同步进度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingSyncModal(
    state: BlockingSyncState,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = {
            if (!state.isLoading) onDismiss()
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Visual Indicator
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            } else if (state.isSuccess == true) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "成功",
                    tint = Color(0xFF00B42A),
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "提示",
                    tint = if (state.needConfigAction) MaterialTheme.colorScheme.primary else Color(0xFFF53F3F),
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title
            Text(
                text = state.title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Message
            Text(
                text = state.message,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Error detail if present
            state.errorDetail?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Start
                    )
                }
            }

            // Action buttons if completed or failed
            if (!state.isLoading) {
                Spacer(modifier = Modifier.height(20.dp))

                if (state.needConfigAction) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("稍后配置")
                        }
                        Button(
                            onClick = onGoToSettings,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("前往配置")
                        }
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (state.isSuccess == true) "完成" else "我知道了")
                    }
                }
            }
        }
    }
}
