package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FeishuTokenRequest(
    @Json(name = "app_id") val appId: String,
    @Json(name = "app_secret") val appSecret: String
)

@JsonClass(generateAdapter = true)
data class FeishuTokenResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "msg") val msg: String = "",
    @Json(name = "tenant_access_token") val tenantAccessToken: String? = null,
    @Json(name = "expire") val expire: Int? = null
)

@JsonClass(generateAdapter = true)
data class BitableRecordCreateRequest(
    @Json(name = "fields") val fields: Map<String, Any>
)

@JsonClass(generateAdapter = true)
data class BitableRecordResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "msg") val msg: String = "",
    @Json(name = "data") val data: BitableSingleRecordData? = null
)

@JsonClass(generateAdapter = true)
data class BitableSingleRecordData(
    @Json(name = "record") val record: BitableRecordItem? = null
)

@JsonClass(generateAdapter = true)
data class BitableRecordListResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "msg") val msg: String = "",
    @Json(name = "data") val data: BitableRecordListData? = null
)

@JsonClass(generateAdapter = true)
data class BitableRecordListData(
    @Json(name = "has_more") val hasMore: Boolean = false,
    @Json(name = "page_token") val pageToken: String? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "items") val items: List<BitableRecordItem>? = null
)

@JsonClass(generateAdapter = true)
data class BitableRecordItem(
    @Json(name = "record_id") val recordId: String? = null,
    @Json(name = "fields") val fields: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class BitableDeleteResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "msg") val msg: String = ""
)

@JsonClass(generateAdapter = true)
data class FeishuMediaUploadResponse(
    @Json(name = "code") val code: Int = 0,
    @Json(name = "msg") val msg: String = "",
    @Json(name = "data") val data: FeishuMediaUploadData? = null
)

@JsonClass(generateAdapter = true)
data class FeishuMediaUploadData(
    @Json(name = "file_token") val fileToken: String? = null
)
