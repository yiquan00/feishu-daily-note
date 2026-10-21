package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

object SyncStatus {
    const val SYNCED = 0
    const val PENDING_UPLOAD = 1
    const val PENDING_UPDATE = 2
    const val PENDING_DELETE = 3
    const val ERROR = 4
}

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mood: String = "",
    val weather: String = "",
    val tags: String = "日常",
    val imageUris: String = "", // Comma-separated paths
    val location: String = "",  // 飞书「定位」
    val feishuRecordId: String? = null,
    val syncStatus: Int = SyncStatus.PENDING_UPLOAD,
    val syncErrorMessage: String? = null,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun getTagList(): List<String> {
        return if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getImageList(): List<String> {
        return if (imageUris.isBlank()) emptyList() else imageUris.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
