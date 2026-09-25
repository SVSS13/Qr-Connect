package com.qrconnect.owner.data.api

import com.qrconnect.owner.data.model.AuthResponse
import com.qrconnect.owner.data.model.LoginRequest
import com.qrconnect.owner.data.model.RefreshRequest
import com.qrconnect.owner.data.model.RegisterRequest
import com.qrconnect.owner.data.model.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<TokenResponse>

    @POST("api/owner/fcm-token")
    suspend fun registerFcmToken(@Body request: com.qrconnect.owner.data.model.FcmTokenRequest): Response<com.qrconnect.owner.data.model.FcmTokenResponse>
}

