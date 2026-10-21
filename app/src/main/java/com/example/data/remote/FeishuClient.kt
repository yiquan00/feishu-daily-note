package com.example.data.remote

import android.util.Log
import com.example.data.local.DiaryEntry
import com.example.data.local.SyncStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class FeishuConfig(
    val appId: String = "",
    val appSecret: String = "",
    val appToken: String = "",
    val tableId: String = "",
    val autoSync: Boolean = true
) {
    val isComplete: Boolean
        get() = appId.isNotBlank() && appSecret.isNotBlank() && appToken.isNotBlank() && tableId.isNotBlank()
}

sealed class FeishuTestResult {
    data class Success(val recordCount: Int, val message: String) : FeishuTestResult()
    data class Failure(val error: String, val suggestion: String) : FeishuTestResult()
}

class FeishuClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val apiService: FeishuApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://open.feishu.cn/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FeishuApiService::class.java)
    }

    private var cachedToken: String? = null
    private var tokenExpireTimeMs: Long = 0L

    suspend fun getValidToken(config: FeishuConfig, forceRefresh: Boolean = false): Result<String> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < tokenExpireTimeMs - 60_000L) {
            return Result.success(cachedToken!!)
        }

        return try {
            val response = apiService.getTenantAccessToken(
                FeishuTokenRequest(
                    appId = config.appId.trim(),
                    appSecret = config.appSecret.trim()
                )
            )

            if (!response.isSuccessful) {
                val err = response.errorBody()?.string() ?: response.message()
                return Result.failure(Exception("获取 Token 失败 (HTTP ${response.code()}): $err"))
            }

            val body = response.body()
            if (body == null || body.code != 0 || body.tenantAccessToken.isNullOrBlank()) {
                val msg = body?.msg ?: "未知错误"
                val suggestion = when (body?.code) {
                    10003 -> "App ID 不存在，请检查开放平台应用凭据"
                    10014 -> "App Secret 不正确，请重新复制密钥"
                    else -> "错误代码: ${body?.code}, 详情: $msg"
                }
                return Result.failure(Exception(suggestion))
            }

            val token = body.tenantAccessToken
            cachedToken = token
            val expireSeconds = body.expire ?: 7200
            tokenExpireTimeMs = now + (expireSeconds * 1000L)
            Result.success(token)
        } catch (e: Exception) {
            Log.e("FeishuClient", "Failed to fetch tenant token", e)
            Result.failure(Exception("网络连接异常: ${e.localizedMessage}"))
        }
    }

    suspend fun testConnection(config: FeishuConfig): FeishuTestResult {
        if (!config.isComplete) {
            return FeishuTestResult.Failure(
                error = "配置信息不完整",
                suggestion = "请填写完整的 App ID、App Secret、多维表格 App Token 和 Table ID"
            )
        }

        val tokenResult = getValidToken(config, forceRefresh = true)
        if (tokenResult.isFailure) {
            return FeishuTestResult.Failure(
                error = tokenResult.exceptionOrNull()?.message ?: "Token 获取失败",
                suggestion = "请确认飞书开放平台应用的 App ID 和 App Secret 是否正确且应用已发布启用"
            )
        }

        val token = tokenResult.getOrThrow()
        return try {
            val response = apiService.getRecords(
                authHeader = "Bearer $token",
                appToken = config.appToken.trim(),
                tableId = config.tableId.trim(),
                pageSize = 20
            )

            if (response.isSuccessful && response.body()?.code == 0) {
                val total = response.body()?.data?.total ?: response.body()?.data?.items?.size ?: 0
                FeishuTestResult.Success(
                    recordCount = total,
                    message = "连接成功！成功访问多维表格，当前包含 $total 条记录。"
                )
            } else {
                val errCode = response.body()?.code ?: response.code()
                val errMsg = response.body()?.msg ?: response.errorBody()?.string() ?: "未知错误"
                val suggestion = when (errCode) {
                    99991663, 99991661 -> "应用缺少权限。请在飞书开放平台 -> 权限管理中添加「查看、编辑、创建多维表格」(bitable:app) 权限并发布新版本"
                    1254043 -> "表格不存在，请核对多维表格 App Token (Base ID) 和 Table ID 是否正确"
                    else -> "飞书返回错误: $errMsg (code: $errCode)"
                }
                FeishuTestResult.Failure(error = errMsg, suggestion = suggestion)
            }
        } catch (e: Exception) {
            FeishuTestResult.Failure(
                error = e.localizedMessage ?: "连接超时",
                suggestion = "请检查网络连接以及飞书服务器连通性"
            )
        }
    }

    suspend fun uploadPhotoFile(config: FeishuConfig, filePath: String): Result<String> {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(Exception("文件不存在或为空"))
        }

        val tokenRes = getValidToken(config)
        if (tokenRes.isFailure) return Result.failure(tokenRes.exceptionOrNull()!!)
        val token = tokenRes.getOrThrow()

        return try {
            val mediaType = "image/*".toMediaTypeOrNull()
            val fileRequestBody = file.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)

            val fileNameBody = file.name.toRequestBody("text/plain".toMediaTypeOrNull())
            val parentTypeBody = "bitable_image".toRequestBody("text/plain".toMediaTypeOrNull())
            val parentNodeBody = config.appToken.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val sizeBody = file.length().toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val res = apiService.uploadMedia(
                authHeader = "Bearer $token",
                fileName = fileNameBody,
                parentType = parentTypeBody,
                parentNode = parentNodeBody,
                size = sizeBody,
                file = filePart
            )

            val body = res.body()
            if (res.isSuccessful && body?.code == 0 && !body.data?.fileToken.isNullOrBlank()) {
                Result.success(body.data!!.fileToken!!)
            } else {
                Result.failure(Exception("上传照片失败: ${body?.msg ?: res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBitableRecord(config: FeishuConfig, entry: DiaryEntry): Result<String> {
        val tokenRes = getValidToken(config)
        if (tokenRes.isFailure) return Result.failure(tokenRes.exceptionOrNull()!!)

        val token = tokenRes.getOrThrow()
        val fields = buildFieldMap(config, entry)

        return try {
            val response = apiService.createRecord(
                authHeader = "Bearer $token",
                appToken = config.appToken.trim(),
                tableId = config.tableId.trim(),
                body = BitableRecordCreateRequest(fields = fields)
            )

            val body = response.body()
            if (response.isSuccessful && body?.code == 0 && body.data?.record?.recordId != null) {
                Result.success(body.data.record.recordId)
            } else {
                val msg = body?.msg ?: response.errorBody()?.string() ?: "创建记录失败"
                Result.failure(Exception("飞书接口错误(${body?.code ?: response.code()}): $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBitableRecord(config: FeishuConfig, entry: DiaryEntry): Result<Unit> {
        val recordId = entry.feishuRecordId ?: return Result.failure(Exception("Record ID 为空"))
        val tokenRes = getValidToken(config)
        if (tokenRes.isFailure) return Result.failure(tokenRes.exceptionOrNull()!!)

        val token = tokenRes.getOrThrow()
        val fields = buildFieldMap(config, entry)

        return try {
            val response = apiService.updateRecord(
                authHeader = "Bearer $token",
                appToken = config.appToken.trim(),
                tableId = config.tableId.trim(),
                recordId = recordId,
                body = BitableRecordCreateRequest(fields = fields)
            )

            val body = response.body()
            if (response.isSuccessful && body?.code == 0) {
                Result.success(Unit)
            } else {
                val msg = body?.msg ?: response.errorBody()?.string() ?: "更新记录失败"
                Result.failure(Exception("飞书接口错误(${body?.code ?: response.code()}): $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBitableRecord(config: FeishuConfig, recordId: String): Result<Unit> {
        val tokenRes = getValidToken(config)
        if (tokenRes.isFailure) return Result.failure(tokenRes.exceptionOrNull()!!)

        val token = tokenRes.getOrThrow()
        return try {
            val response = apiService.deleteRecord(
                authHeader = "Bearer $token",
                appToken = config.appToken.trim(),
                tableId = config.tableId.trim(),
                recordId = recordId
            )
            val body = response.body()
            if (response.isSuccessful && body?.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除记录失败: ${body?.msg ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllRecords(config: FeishuConfig): Result<List<DiaryEntry>> {
        val tokenRes = getValidToken(config)
        if (tokenRes.isFailure) return Result.failure(tokenRes.exceptionOrNull()!!)

        val token = tokenRes.getOrThrow()
        return try {
            val response = apiService.getRecords(
                authHeader = "Bearer $token",
                appToken = config.appToken.trim(),
                tableId = config.tableId.trim(),
                pageSize = 100
            )

            val body = response.body()
            if (response.isSuccessful && body?.code == 0) {
                val items = body.data?.items ?: emptyList()
                val entries = items.mapNotNull { item ->
                    mapItemToEntry(item)
                }
                Result.success(entries)
            } else {
                val msg = body?.msg ?: response.errorBody()?.string() ?: "获取表格记录失败"
                Result.failure(Exception("飞书接口错误: $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 构建飞书多维表格 5 大精准字段：
     * 1. 日记 (文本) - 仅正文，无标题项
     * 2. 日记日期 (日期) - 毫秒时间戳
     * 3. 标签 (多选) - 仅限：生活、工作、学习
     * 4. 定位 (定位) - 地址名称与坐标
     * 5. 照片 (文件) - 飞书文件列表
     */
    private suspend fun buildFieldMap(config: FeishuConfig, entry: DiaryEntry): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()

        // 1. 日记 (文本) - 用户明确要求：没有日记标题这一项，只有日记（文本）
        fields["日记"] = entry.content

        // 2. 日记日期 (日期 - 毫秒时间戳)
        fields["日记日期"] = entry.timestamp

        // 3. 标签 (多选) - 仅限：生活、工作、学习
        val validTags = listOf("生活", "工作", "学习")
        val tagList = entry.getTagList().filter { it in validTags }
        fields["标签"] = if (tagList.isNotEmpty()) tagList else listOf("生活")

        // 4. 定位 (文本内容 - 用户明确要求：定位应该是一串文本内容)
        if (entry.location.isNotBlank()) {
            fields["定位"] = entry.location.trim()
        }

        // 5. 照片 (文件)
        val imagePaths = entry.getImageList()
        val fileTokens = mutableListOf<Map<String, String>>()
        for (path in imagePaths) {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                // Already remote URL or token
                continue
            }
            // Try uploading local image to Feishu bitable attachment
            try {
                val uploadRes = uploadPhotoFile(config, path)
                if (uploadRes.isSuccess) {
                    val token = uploadRes.getOrThrow()
                    fileTokens.add(mapOf("file_token" to token))
                }
            } catch (e: Exception) {
                Log.w("FeishuClient", "Failed to upload image $path to Feishu", e)
            }
        }
        if (fileTokens.isNotEmpty()) {
            fields["照片"] = fileTokens
        }

        return fields
    }

    private fun mapItemToEntry(item: BitableRecordItem): DiaryEntry? {
        val recId = item.recordId ?: return null
        val fields = item.fields

        // 1. 日记 (文本) - 无标题，直接读取日记内容
        val rawDiary = (fields["日记"] as? String)
            ?: (fields["内容"] as? String)
            ?: (fields["Diary"] as? String)
            ?: ""

        // 2. 日记日期 (日期)
        val rawDate = fields["日记日期"] ?: fields["日期"] ?: fields["Date"]
        val timestamp = when (rawDate) {
            is Number -> rawDate.toLong()
            is String -> tryParseDate(rawDate) ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }

        // 3. 标签 (多选) - 仅保留 生活、工作、学习
        val validTags = setOf("生活", "工作", "学习")
        val rawTags = fields["标签"] ?: fields["Tags"]
        val tagList = when (rawTags) {
            is List<*> -> rawTags.mapNotNull { it?.toString() }.filter { it in validTags }
            is String -> rawTags.split(",", "，", " ").map { it.trim() }.filter { it in validTags }
            else -> listOf("生活")
        }
        val tags = if (tagList.isNotEmpty()) tagList.joinToString(",") else "生活"

        // 4. 照片 (文件)
        val rawPhotos = fields["照片"] ?: fields["图片链接"] ?: fields["图片"] ?: fields["Images"]
        val images = when (rawPhotos) {
            is List<*> -> {
                rawPhotos.mapNotNull { photoItem ->
                    when (photoItem) {
                        is Map<*, *> -> (photoItem["url"] ?: photoItem["tmp_url"] ?: photoItem["name"] ?: photoItem["file_token"]) as? String
                        is String -> photoItem
                        else -> null
                    }
                }.joinToString(",")
            }
            is String -> rawPhotos
            else -> ""
        }

        // 5. 定位 (文本或地图对象)
        val rawLocation = fields["定位"] ?: fields["位置"] ?: fields["Location"]
        val location = when (rawLocation) {
            is String -> rawLocation.trim()
            is Map<*, *> -> {
                ((rawLocation["full_address"] ?: rawLocation["address"] ?: rawLocation["name"] ?: rawLocation["location"]) as? String) ?: ""
            }
            else -> rawLocation?.toString()?.trim() ?: ""
        }

        return DiaryEntry(
            id = "feishu_$recId",
            title = "",
            content = rawDiary,
            timestamp = timestamp,
            tags = tags,
            imageUris = images,
            location = location,
            feishuRecordId = recId,
            syncStatus = SyncStatus.SYNCED,
            syncErrorMessage = null,
            lastModified = timestamp
        )
    }

    private fun tryParseDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.CHINA)
                val d = sdf.parse(dateStr)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }
}
