package com.example.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface FeishuApiService {

    @POST("open-apis/auth/v3/tenant_access_token/internal")
    suspend fun getTenantAccessToken(
        @Body request: FeishuTokenRequest
    ): Response<FeishuTokenResponse>

    @GET("open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records")
    suspend fun getRecords(
        @Header("Authorization") authHeader: String,
        @Path("app_token") appToken: String,
        @Path("table_id") tableId: String,
        @Query("page_size") pageSize: Int = 100
    ): Response<BitableRecordListResponse>

    @POST("open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records")
    suspend fun createRecord(
        @Header("Authorization") authHeader: String,
        @Path("app_token") appToken: String,
        @Path("table_id") tableId: String,
        @Body body: BitableRecordCreateRequest
    ): Response<BitableRecordResponse>

    @PUT("open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}")
    suspend fun updateRecord(
        @Header("Authorization") authHeader: String,
        @Path("app_token") appToken: String,
        @Path("table_id") tableId: String,
        @Path("record_id") recordId: String,
        @Body body: BitableRecordCreateRequest
    ): Response<BitableRecordResponse>

    @DELETE("open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}")
    suspend fun deleteRecord(
        @Header("Authorization") authHeader: String,
        @Path("app_token") appToken: String,
        @Path("table_id") tableId: String,
        @Path("record_id") recordId: String
    ): Response<BitableDeleteResponse>

    @Multipart
    @POST("open-apis/drive/v1/medias/upload_all")
    suspend fun uploadMedia(
        @Header("Authorization") authHeader: String,
        @Part("file_name") fileName: RequestBody,
        @Part("parent_type") parentType: RequestBody,
        @Part("parent_node") parentNode: RequestBody,
        @Part("size") size: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<FeishuMediaUploadResponse>
}
