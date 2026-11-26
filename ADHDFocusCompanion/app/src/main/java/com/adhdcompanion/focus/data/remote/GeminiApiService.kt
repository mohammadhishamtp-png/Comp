package com.adhdcompanion.focus.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST

/**
 * Talks directly to the Gemini REST API using the user's own API key as a
 * query parameter, exactly as Google's generateContent endpoint expects.
 * No key ever touches any server of ours — there isn't one.
 */
interface GeminiApiService {

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String,
        @Query("pageSize") pageSize: Int = 100
    ): GeminiModelListResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateSpeech(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiTtsResponse

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}
