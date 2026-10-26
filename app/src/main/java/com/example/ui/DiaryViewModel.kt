package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DiaryEntry
import com.example.data.remote.FeishuConfig
import com.example.data.remote.FeishuTestResult
import com.example.data.repository.DiaryRepository
import com.example.data.repository.SyncResult
import com.example.widget.DiaryAppWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    FEED,
    EDITOR,
    DETAIL,
    FEISHU_SETTINGS
}

data class BlockingSyncState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val title: String = "正在同步飞书...",
    val message: String = "请稍候...",
    val isSuccess: Boolean? = null,
    val errorDetail: String? = null,
    val needConfigAction: Boolean = false
)

data class DiaryUiState(
    val currentScreen: Screen = Screen.FEED,
    val activeDiaryId: String? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val testConnectionResult: FeishuTestResult? = null,
    val isTestingConnection: Boolean = false
)

class DiaryViewModel(
    private val repository: DiaryRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _blockingSyncState = MutableStateFlow(BlockingSyncState())
    val blockingSyncState: StateFlow<BlockingSyncState> = _blockingSyncState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    val feishuConfig: StateFlow<FeishuConfig> = repository.preferences.configFlow
    val themeMode: StateFlow<String> = repository.preferences.themeModeFlow
    val lastSyncTime: StateFlow<Long> = repository.preferences.lastSyncTimeFlow
    val widgetTag: StateFlow<String> = repository.preferences.widgetTagFlow
    val widgetRandomMode: StateFlow<Boolean> = repository.preferences.widgetRandomModeFlow

    val appLockEnabled: StateFlow<Boolean> = repository.preferences.appLockEnabledFlow
    val editorFontSize: StateFlow<Int> = repository.preferences.editorFontSizeFlow

    fun setEditorFontSize(size: Int) {
        repository.preferences.saveEditorFontSize(size)
    }

    private val _isUnlocked = MutableStateFlow(!repository.preferences.loadAppLockEnabled())
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    init {
        // 每次启动或进入应用时主动刷新并同步
        refreshSync()
    }

    // Filtered entries stream (by search query and selected tag)
    val entries: StateFlow<List<DiaryEntry>> = combine(
        repository.entries,
        _searchQuery,
        _selectedTag
    ) { list, query, tag ->
        list.filter { entry ->
            val matchQuery = query.isBlank() ||
                entry.content.contains(query, ignoreCase = true) ||
                entry.tags.contains(query, ignoreCase = true) ||
                entry.location.contains(query, ignoreCase = true)

            val matchTag = tag == null || entry.getTagList().contains(tag)

            matchQuery && matchTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All entries filtered only by search query (for HorizontalPager tab slicing)
    val allEntries: StateFlow<List<DiaryEntry>> = combine(
        repository.entries,
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter { entry ->
                entry.content.contains(query, ignoreCase = true) ||
                entry.tags.contains(query, ignoreCase = true) ||
                entry.location.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 用户明确要求：标签只有 生活、工作、学习 这几个，其他不用展示
    val allTags: StateFlow<List<String>> = MutableStateFlow(listOf("生活", "工作", "学习"))

    fun navigateTo(screen: Screen, diaryId: String? = null) {
        _uiState.value = _uiState.value.copy(
            currentScreen = screen,
            activeDiaryId = diaryId
        )
    }

    fun navigateBack() {
        _uiState.value = _uiState.value.copy(
            currentScreen = Screen.FEED,
            activeDiaryId = null,
            testConnectionResult = null
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTagFilter(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    /**
     * 增 / 改 (Create / Update):
     * 每次保存必须同步飞书多维表格，弹出阻断弹窗，禁止用户其他操作
     */
    fun saveDiary(
        id: String?,
        content: String,
        timestamp: Long,
        tags: String,
        location: String,
        images: List<String>,
        onSuccess: () -> Unit
    ) {
        val config = repository.preferences.loadConfig()

        if (!config.isComplete) {
            // Feishu not yet configured: save locally and block UI with prompt
            viewModelScope.launch {
                repository.saveEntry(
                    id = id,
                    content = content,
                    timestamp = timestamp,
                    tags = tags.ifBlank { "生活" },
                    location = location,
                    imageUris = images
                )
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "已保存",
                    message = "已保存在本地。配置飞书后可自动同步。",
                    isSuccess = true,
                    needConfigAction = true
                )
            }
            return
        }

        // Show blocking sync dialog
        val isNew = (id == null)
        _blockingSyncState.value = BlockingSyncState(
            isVisible = true,
            isLoading = true,
            title = if (isNew) "正在保存并同步..." else "正在更新并同步...",
            message = "请稍候..."
        )

        viewModelScope.launch {
            val savedEntry = repository.saveEntry(
                id = id,
                content = content,
                timestamp = timestamp,
                tags = tags.ifBlank { "生活" },
                location = location,
                imageUris = images
            )
            val syncSuccess = repository.syncSingleEntry(savedEntry, config)
            if (syncSuccess) {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "同步成功",
                    message = "已同步至飞书多维表格",
                    isSuccess = true
                )
                delay(850)
                _blockingSyncState.value = BlockingSyncState(isVisible = false)
                onSuccess()
                navigateBack()
            } else {
                val current = repository.getEntryById(savedEntry.id)
                val err = current?.syncErrorMessage ?: "同步超时或响应异常"
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "同步遇到问题",
                    message = "日记已保存在本地",
                    errorDetail = err,
                    isSuccess = false
                )
            }
        }
    }

    /**
     * 删 (Delete):
     * 删除日记时阻断操作，同步从飞书多维表格移除对应记录
     */
    fun deleteDiary(entry: DiaryEntry) {
        val config = repository.preferences.loadConfig()

        if (!config.isComplete || entry.feishuRecordId == null) {
            viewModelScope.launch {
                repository.deleteEntry(entry)
                DiaryAppWidgetProvider.updateAllWidgets(appContext)
                _uiState.value = _uiState.value.copy(syncMessage = "日记已删除")
                navigateBack()
            }
            return
        }

        // Show blocking sync dialog for deletion
        _blockingSyncState.value = BlockingSyncState(
            isVisible = true,
            isLoading = true,
            title = "正在删除...",
            message = "请稍候..."
        )

        viewModelScope.launch {
            val remoteOk = repository.deleteEntry(entry)
            DiaryAppWidgetProvider.updateAllWidgets(appContext)
            if (remoteOk) {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "删除成功",
                    message = "已从飞书多维表格移除",
                    isSuccess = true
                )
                delay(800)
                _blockingSyncState.value = BlockingSyncState(isVisible = false)
                _uiState.value = _uiState.value.copy(syncMessage = "日记已删除并从飞书移除")
                navigateBack()
            } else {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "本地已删除",
                    message = "从飞书移除时出现异常，请检查网络",
                    isSuccess = false
                )
                _uiState.value = _uiState.value.copy(syncMessage = "本地已删除，飞书同步异常")
            }
        }
    }

    /**
     * 下拉刷新加载同步 (Pull-to-refresh sync):
     * 在首页下拉触发，非阻断式加载同步
     */
    fun refreshSync(onComplete: (() -> Unit)? = null) {
        val config = repository.preferences.loadConfig()
        _uiState.value = _uiState.value.copy(isSyncing = true)

        viewModelScope.launch {
            if (!config.isComplete) {
                delay(400)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "未配置飞书多维表格，已刷新本地日记"
                )
                DiaryAppWidgetProvider.updateAllWidgets(appContext)
                onComplete?.invoke()
                return@launch
            }

            val res = repository.syncAll()
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncMessage = if (res.success) "已同步最新日记 (${res.message})" else "同步失败：${res.message}"
            )
            DiaryAppWidgetProvider.updateAllWidgets(appContext)
            onComplete?.invoke()
        }
    }

    /**
     * 查 (Query / Full Sync):
     * 查询飞书最新记录并双向同步，弹出阻断弹窗
     */
    fun syncAll(onResult: ((SyncResult) -> Unit)? = null) {
        val config = repository.preferences.loadConfig()
        if (!config.isComplete) {
            _blockingSyncState.value = BlockingSyncState(
                isVisible = true,
                isLoading = false,
                title = "未配置飞书多维表格",
                message = "请先在「设置」中填写 App ID、App Secret、App Token 和 Table ID。",
                isSuccess = false,
                needConfigAction = true
            )
            return
        }

        _blockingSyncState.value = BlockingSyncState(
            isVisible = true,
            isLoading = true,
            title = "正在查询并同步飞书多维表格",
            message = "正在与飞书服务器通信，查询最新日记记录并更新本地，请稍候..."
        )

        viewModelScope.launch {
            val res = repository.syncAll()
            if (res.success) {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "飞书多维表格同步完成",
                    message = res.message,
                    isSuccess = true
                )
                delay(950)
                _blockingSyncState.value = BlockingSyncState(isVisible = false)
            } else {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "飞书查询同步失败",
                    message = res.message,
                    errorDetail = "请核对应用权限(bitable:app)及网络连通性",
                    isSuccess = false
                )
            }
            onResult?.invoke(res)
        }
    }

    fun syncSingle(entry: DiaryEntry) {
        val config = repository.preferences.loadConfig()
        if (!config.isComplete) {
            _blockingSyncState.value = BlockingSyncState(
                isVisible = true,
                isLoading = false,
                title = "未配置飞书多维表格",
                message = "请先在「设置」中完成飞书凭证配置。",
                isSuccess = false,
                needConfigAction = true
            )
            return
        }

        _blockingSyncState.value = BlockingSyncState(
            isVisible = true,
            isLoading = true,
            title = "正在同步该篇日记",
            message = "正在将本篇日记提交至飞书多维表格..."
        )

        viewModelScope.launch {
            val success = repository.syncSingleEntry(entry, config)
            if (success) {
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "同步成功",
                    message = "本篇日记已成功推送到飞书多维表格！",
                    isSuccess = true
                )
                delay(800)
                _blockingSyncState.value = BlockingSyncState(isVisible = false)
            } else {
                val current = repository.getEntryById(entry.id)
                val err = current?.syncErrorMessage ?: "同步失败"
                _blockingSyncState.value = BlockingSyncState(
                    isVisible = true,
                    isLoading = false,
                    title = "同步失败",
                    message = "未能成功同步至飞书多维表格",
                    errorDetail = err,
                    isSuccess = false
                )
            }
        }
    }

    fun dismissBlockingSync() {
        _blockingSyncState.value = BlockingSyncState(isVisible = false)
    }

    fun saveFeishuConfig(config: FeishuConfig) {
        repository.preferences.saveConfig(config)
    }

    fun testFeishuConnection(config: FeishuConfig) {
        _uiState.value = _uiState.value.copy(
            isTestingConnection = true,
            testConnectionResult = null
        )
        viewModelScope.launch {
            val client = com.example.data.remote.FeishuClient()
            val result = client.testConnection(config)
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testConnectionResult = result
            )
        }
    }

    fun setThemeMode(mode: String) {
        repository.preferences.saveThemeMode(mode)
    }

    fun setWidgetTag(tag: String) {
        repository.preferences.saveWidgetTag(tag)
        repository.preferences.saveWidgetIndex(0)
        DiaryAppWidgetProvider.updateAllWidgets(appContext)
    }

    fun setWidgetRandomMode(enabled: Boolean) {
        repository.preferences.saveWidgetRandomMode(enabled)
        DiaryAppWidgetProvider.updateAllWidgets(appContext)
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        if (repository.preferences.loadAppLockEnabled()) {
            _isUnlocked.value = false
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        repository.preferences.saveAppLockEnabled(enabled)
        if (!enabled) {
            _isUnlocked.value = true
        } else {
            _isUnlocked.value = false
        }
    }
}

class DiaryViewModelFactory(
    private val repository: DiaryRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            return DiaryViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
