package com.qrconnect.owner.data.api

import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.qrconnect.owner.data.model.TokenResponse
import com.qrconnect.owner.ui.auth.LoginActivity
import com.qrconnect.owner.util.Constants
import com.qrconnect.owner.util.TokenManager
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface SystemApi {
    @GET("health")
    suspend fun healthCheck(): retrofit2.Response<Map<String, Any>>
}

object ApiClient {
    private const val TAG = "ApiClient"
    private var baseUrl = Constants.DEFAULT_BASE_URL
    private val gson = Gson()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = TokenManager.getAccessToken()

        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        } else {
            original.newBuilder()
                .header("Accept", "application/json")
                .build()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Prevent endless retry loop
            if (responseCount(response) >= 3) {
                Log.w(TAG, "TokenAuthenticator: Exceeded 3 retry attempts, clearing session")
                handleSessionExpired()
                return null
            }

            // Do not attempt refresh on auth endpoints
            val path = response.request.url.encodedPath
            if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
                return null
            }

            synchronized(this) {
                val currentAccessToken = TokenManager.getAccessToken()
                val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

                // If another concurrent thread already refreshed the token, retry with updated token
                if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestToken) {
                    Log.d(TAG, "TokenAuthenticator: Access token already refreshed by another thread, retrying")
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                val refreshToken = TokenManager.getRefreshToken()
                if (refreshToken.isNullOrBlank() || TokenManager.isRefreshTokenExpired()) {
                    Log.w(TAG, "TokenAuthenticator: Refresh token is missing or expired")
                    handleSessionExpired()
                    return null
                }

                Log.d(TAG, "TokenAuthenticator: Refreshing access token via API...")
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = gson.toJson(mapOf("refresh_token" to refreshToken)).toRequestBody(mediaType)
                val refreshRequest = Request.Builder()
                    .url("${baseUrl}api/auth/refresh")
                    .post(requestBody)
                    .build()

                val refreshClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                try {
                    val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                    if (refreshResponse.isSuccessful) {
                        val bodyString = refreshResponse.body?.string()
                        val tokenResponse = gson.fromJson(bodyString, TokenResponse::class.java)
                        if (tokenResponse != null && !tokenResponse.accessToken.isNullOrBlank()) {
                            Log.i(TAG, "TokenAuthenticator: Successfully refreshed access token")
                            TokenManager.saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                                .build()
                        }
                    } else {
                        Log.w(TAG, "TokenAuthenticator: Refresh endpoint returned HTTP ${refreshResponse.code}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TokenAuthenticator: Failed to refresh token", e)
                }

                // If refresh failed (e.g. 401 or invalid token), clean up and redirect
                handleSessionExpired()
                return null
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }

        private fun handleSessionExpired() {
            TokenManager.clearCurrentSession()
            TokenManager.getContext()?.let { ctx ->
                try {
                    val intent = Intent(ctx, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("EXTRA_SESSION_EXPIRED", true)
                    }
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching LoginActivity on session expiration", e)
                }
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .authenticator(tokenAuthenticator)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val cardsApi: CardsApi by lazy { retrofit.create(CardsApi::class.java) }
    val actionsApi: ActionsApi by lazy { retrofit.create(ActionsApi::class.java) }
    val eventsApi: EventsApi by lazy { retrofit.create(EventsApi::class.java) }
    val systemApi: SystemApi by lazy { retrofit.create(SystemApi::class.java) }

    fun setBaseUrl(newBaseUrl: String) {
        baseUrl = if (newBaseUrl.endsWith("/")) newBaseUrl else "$newBaseUrl/"
    }
}
