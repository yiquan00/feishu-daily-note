package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.DiaryDao
import com.example.data.local.DiaryDatabase
import com.example.data.local.DiaryEntry
import com.example.data.local.SyncStatus
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.FeishuClient
import com.example.data.remote.FeishuConfig
import com.example.widget.DiaryAppWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SyncResult(
    val success: Boolean,
    val uploadedCount: Int = 0,
    val pulledCount: Int = 0,
    val message: String = ""
)

class DiaryRepository(
    private val context: Context,
    private val dao: DiaryDao = DiaryDatabase.getDatabase(context).diaryDao(),
    private val feishuClient: FeishuClient = FeishuClient(),
    val preferences: UserPreferencesRepository = UserPreferencesRepository(context)
) {
    val entries: Flow<List<DiaryEntry>> = dao.getAll()

    fun searchEntries(query: String): Flow<List<DiaryEntry>> = dao.search(query)

    suspend fun getEntryById(id: String): DiaryEntry? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun getLatestEntry(): DiaryEntry? = withContext(Dispatchers.IO) {
        dao.getLatest()
    }

    suspend fun saveEntry(
        id: String? = null,
        content: String,
        timestamp: Long,
        tags: String,
        location: String,
        imageUris: List<String>
    ): DiaryEntry = withContext(Dispatchers.IO) {
        val existing = if (id != null) dao.getById(id) else null

        // Copy any newly added content URI photos to internal storage
        val persistedImagePaths = imageUris.map { uriStr ->
            persistImageIfNecessary(uriStr)
        }
        val imagesJoined = persistedImagePaths.joinToString(",")

        // Ensure tags only contain allowed tags: 生活, 工作, 学习
        val validTags = listOf("生活", "工作", "学习")
        val cleanTags = tags.split(",", "，", " ")
            .map { it.trim() }
            .filter { it in validTags }
            .ifEmpty { listOf("生活") }
            .joinToString(",")

        val entryToSave = if (existing != null) {
            existing.copy(
                title = "",
                content = content,
                timestamp = timestamp,
                tags = cleanTags,
                location = location,
                imageUris = imagesJoined,
                syncStatus = if (existing.feishuRecordId != null) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_UPLOAD,
                syncErrorMessage = null,
                lastModified = System.currentTimeMillis()
            )
        } else {
            DiaryEntry(
                id = UUID.randomUUID().toString(),
                title = "",
                content = content,
                timestamp = timestamp,
                tags = cleanTags,
                location = location,
                imageUris = imagesJoined,
                feishuRecordId = null,
                syncStatus = SyncStatus.PENDING_UPLOAD,
                syncErrorMessage = null,
                lastModified = System.currentTimeMillis()
            )
        }

        dao.insert(entryToSave)
        DiaryAppWidgetProvider.updateAllWidgets(context)

        entryToSave
    }

    suspend fun deleteEntry(entry: DiaryEntry): Boolean = withContext(Dispatchers.IO) {
        val config = preferences.loadConfig()
        var remoteSuccess = true
        if (entry.feishuRecordId != null && config.isComplete) {
            val deleteRes = feishuClient.deleteBitableRecord(config, entry.feishuRecordId)
            if (deleteRes.isFailure) {
                Log.w("DiaryRepository", "Remote delete failed: ${deleteRes.exceptionOrNull()?.message}")
                remoteSuccess = false
            }
        }
        dao.delete(entry)
        DiaryAppWidgetProvider.updateAllWidgets(context)
        remoteSuccess
    }

    suspend fun syncSingleEntry(entry: DiaryEntry, config: FeishuConfig): Boolean = withContext(Dispatchers.IO) {
        if (!config.isComplete) return@withContext false

        return@withContext try {
            if (entry.feishuRecordId == null) {
                val createRes = feishuClient.createBitableRecord(config, entry)
                if (createRes.isSuccess) {
                    val recId = createRes.getOrThrow()
                    dao.update(
                        entry.copy(
                            feishuRecordId = recId,
                            syncStatus = SyncStatus.SYNCED,
                            syncErrorMessage = null
                        )
                    )
                    DiaryAppWidgetProvider.updateAllWidgets(context)
                    true
                } else {
                    val err = createRes.exceptionOrNull()?.message ?: "同步失败"
                    dao.update(
                        entry.copy(
                            syncStatus = SyncStatus.ERROR,
                            syncErrorMessage = err
                        )
                    )
                    false
                }
            } else {
                val updateRes = feishuClient.updateBitableRecord(config, entry)
                if (updateRes.isSuccess) {
                    dao.update(
                        entry.copy(
                            syncStatus = SyncStatus.SYNCED,
                            syncErrorMessage = null
                        )
                    )
                    DiaryAppWidgetProvider.updateAllWidgets(context)
                    true
                } else {
                    val err = updateRes.exceptionOrNull()?.message ?: "同步失败"
                    dao.update(
                        entry.copy(
                            syncStatus = SyncStatus.ERROR,
                            syncErrorMessage = err
                        )
                    )
                    false
                }
            }
        } catch (e: Exception) {
            dao.update(
                entry.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncErrorMessage = e.localizedMessage
                )
            )
            false
        }
    }

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val config = preferences.loadConfig()
        if (!config.isComplete) {
            return@withContext SyncResult(
                success = false,
                message = "未配置飞书多维表格参数，请在设置中填写 App ID 和 Table ID"
            )
        }

        var uploaded = 0
        var pulled = 0

        try {
            // 1. Process Pending Deletes
            val pendingDeletes = dao.getPendingDelete()
            for (del in pendingDeletes) {
                if (del.feishuRecordId != null) {
                    feishuClient.deleteBitableRecord(config, del.feishuRecordId)
                }
                dao.delete(del)
            }

            // 2. Process Pending Uploads
            val pendingUploads = dao.getPendingUpload()
            for (item in pendingUploads) {
                val res = feishuClient.createBitableRecord(config, item)
                if (res.isSuccess) {
                    dao.update(
                        item.copy(
                            feishuRecordId = res.getOrThrow(),
                            syncStatus = SyncStatus.SYNCED,
                            syncErrorMessage = null
                        )
                    )
                    uploaded++
                } else {
                    dao.update(
                        item.copy(
                            syncStatus = SyncStatus.ERROR,
                            syncErrorMessage = res.exceptionOrNull()?.message
                        )
                    )
                }
            }

            // 3. Process Pending Updates
            val pendingUpdates = dao.getPendingUpdate()
            for (item in pendingUpdates) {
                if (item.feishuRecordId != null) {
                    val res = feishuClient.updateBitableRecord(config, item)
                    if (res.isSuccess) {
                        dao.update(
                            item.copy(
                                syncStatus = SyncStatus.SYNCED,
                                syncErrorMessage = null
                            )
                        )
                        uploaded++
                    } else {
                        dao.update(
                            item.copy(
                                syncStatus = SyncStatus.ERROR,
                                syncErrorMessage = res.exceptionOrNull()?.message
                            )
                        )
                    }
                }
            }

            // 4. Pull Remote Records from Feishu (查)
            val pullRes = feishuClient.fetchAllRecords(config)
            if (pullRes.isSuccess) {
                val remoteEntries = pullRes.getOrThrow()
                for (remote in remoteEntries) {
                    val existing = if (remote.feishuRecordId != null) {
                        dao.getByFeishuRecordId(remote.feishuRecordId)
                    } else null

                    if (existing == null) {
                        dao.insert(remote)
                        pulled++
                    } else if (existing.syncStatus == SyncStatus.SYNCED && remote.lastModified > existing.lastModified) {
                        dao.update(
                            remote.copy(
                                id = existing.id,
                                imageUris = if (remote.imageUris.isNotBlank()) remote.imageUris else existing.imageUris
                            )
                        )
                        pulled++
                    }
                }
            } else {
                return@withContext SyncResult(
                    success = false,
                    uploadedCount = uploaded,
                    pulledCount = 0,
                    message = "拉取飞书记录失败: ${pullRes.exceptionOrNull()?.message}"
                )
            }

            preferences.updateLastSyncTime()
            DiaryAppWidgetProvider.updateAllWidgets(context)

            SyncResult(
                success = true,
                uploadedCount = uploaded,
                pulledCount = pulled,
                message = "飞书多维表格同步成功！已同步上传/修改 $uploaded 条，拉取最新 $pulled 条"
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                uploadedCount = uploaded,
                pulledCount = pulled,
                message = "同步失败: ${e.localizedMessage}"
            )
        }
    }

    private fun persistImageIfNecessary(uriString: String): String {
        if (!uriString.startsWith("content://")) {
            return uriString
        }
        return try {
            val uri = Uri.parse(uriString)
            val dir = File(context.filesDir, "diary_images").apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("DiaryRepository", "Error copying image URI to local file", e)
            uriString
        }
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = dao.getLatest()
        if (count == null) {
            val now = System.currentTimeMillis()
            val samples = listOf(
                DiaryEntry(
                    id = "sample_1",
                    title = "",
                    content = "今天早上6点准时醒来，换上跑鞋跑了五公里。迎着初升的晨光，微风徐徐，草木清香扑鼻。生活的小美好大概就是这样吧，元气满满地开始新的一天！",
                    timestamp = now - (1000L * 60 * 60 * 3),
                    tags = "生活",
                    location = "朝阳公园",
                    imageUris = "",
                    feishuRecordId = null,
                    syncStatus = SyncStatus.PENDING_UPLOAD
                ),
                DiaryEntry(
                    id = "sample_2",
                    title = "",
                    content = "整理了本周项目需求和架构方案，把核心业务逻辑和数据同步链路理顺了。完成里程碑交付，团队小伙伴配合默契，很有成就感的一天。",
                    timestamp = now - (1000L * 60 * 60 * 24),
                    tags = "工作",
                    location = "科技园A座",
                    imageUris = "",
                    feishuRecordId = null,
                    syncStatus = SyncStatus.PENDING_UPLOAD
                ),
                DiaryEntry(
                    id = "sample_3",
                    title = "",
                    content = "阅读了 Jetpack Compose 深度优化指南和 Kotlin 协程调度机制，做了一篇系统性的读书笔记。温故而知新，夯实基础才能走得更远。",
                    timestamp = now - (1000L * 60 * 60 * 48),
                    tags = "学习",
                    location = "市图书馆",
                    imageUris = "",
                    feishuRecordId = null,
                    syncStatus = SyncStatus.PENDING_UPLOAD
                )
            )
            dao.insertAll(samples)
            DiaryAppWidgetProvider.updateAllWidgets(context)
        }
    }
}
