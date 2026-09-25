package com.qrconnect.owner.data.repository

import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.AuthResponse
import com.qrconnect.owner.data.model.LoginRequest
import com.qrconnect.owner.util.NetworkResult
import com.qrconnect.owner.util.TokenManager

class AuthRepository {
    private val api = ApiClient.authApi

    suspend fun login(email: String, password: String): NetworkResult<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                TokenManager.saveSession(
                    userId = auth.user.id,
                    name = auth.user.name,
                    email = auth.user.email,
                    accessToken = auth.accessToken,
                    refreshToken = auth.refreshToken
                )
                NetworkResult.Success(auth)
            } else {
                NetworkResult.Error("Login failed: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun register(name: String, email: String, password: String): NetworkResult<AuthResponse> {
        return try {
            val response = api.register(com.qrconnect.owner.data.model.RegisterRequest(name, email, password))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                TokenManager.saveSession(
                    userId = auth.user.id,
                    name = auth.user.name,
                    email = auth.user.email,
                    accessToken = auth.accessToken,
                    refreshToken = auth.refreshToken
                )
                NetworkResult.Success(auth)
            } else {
                NetworkResult.Error("Registration failed: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun logout() {
        TokenManager.clear()
    }

    fun isLoggedIn(): Boolean = TokenManager.isLoggedIn()
}
