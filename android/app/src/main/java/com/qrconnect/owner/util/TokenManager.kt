package com.qrconnect.owner.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.Serializable

data class SavedAccount(
    val userId: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String
) : Serializable

object TokenManager {
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val gson = Gson()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getContext(): Context? = appContext

    fun saveSession(
        userId: String,
        name: String,
        email: String,
        accessToken: String,
        refreshToken: String
    ) {
        prefs?.edit()
            ?.putString(Constants.KEY_USER_ID, userId)
            ?.putString(Constants.KEY_USER_NAME, name)
            ?.putString(Constants.KEY_USER_EMAIL, email)
            ?.putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            ?.putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()

        val accounts = getSavedAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.userId == userId || it.email.equals(email, ignoreCase = true) }
        val account = SavedAccount(userId, name, email, accessToken, refreshToken)
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }
        saveAccountsList(accounts)
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs?.edit()
            ?.putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            ?.putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()
        updateCurrentAccountInSavedList()
    }

    fun getAccessToken(): String? = prefs?.getString(Constants.KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs?.getString(Constants.KEY_REFRESH_TOKEN, null)

    fun saveUserData(id: String, name: String, email: String) {
        prefs?.edit()
            ?.putString(Constants.KEY_USER_ID, id)
            ?.putString(Constants.KEY_USER_NAME, name)
            ?.putString(Constants.KEY_USER_EMAIL, email)
            ?.apply()
        updateCurrentAccountInSavedList()
    }

    fun getUserId(): String? = prefs?.getString(Constants.KEY_USER_ID, null)

    fun getUserName(): String? = prefs?.getString(Constants.KEY_USER_NAME, null)

    fun getUserEmail(): String? = prefs?.getString(Constants.KEY_USER_EMAIL, null)

    fun saveFcmToken(token: String) {
        prefs?.edit()?.putString(Constants.KEY_FCM_TOKEN, token)?.apply()
    }

    fun getFcmToken(): String? = prefs?.getString(Constants.KEY_FCM_TOKEN, null)

    fun isAccessTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return isJwtExpired(token, marginSeconds = 10)
    }

    fun isRefreshTokenExpired(): Boolean {
        val token = getRefreshToken() ?: return true
        return isJwtExpired(token, marginSeconds = 0)
    }

    private fun isJwtExpired(jwt: String, marginSeconds: Long = 0): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return true
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val exp = json.optLong("exp", 0L)
            if (exp == 0L) return false
            val currentSeconds = System.currentTimeMillis() / 1000
            currentSeconds >= (exp - marginSeconds)
        } catch (e: Exception) {
            true
        }
    }

    fun isLoggedIn(): Boolean {
        val access = getAccessToken()
        val refresh = getRefreshToken()
        if (access.isNullOrBlank() && refresh.isNullOrBlank()) return false
        // If refresh token exists and is valid, the session is active/recoverable
        if (!refresh.isNullOrBlank() && !isRefreshTokenExpired()) return true
        // If access token exists and is still valid
        if (!access.isNullOrBlank() && !isAccessTokenExpired()) return true
        return false
    }

    private fun updateCurrentAccountInSavedList() {
        val userId = getUserId() ?: return
        val name = getUserName() ?: "Owner"
        val email = getUserEmail() ?: ""
        val access = getAccessToken() ?: return
        val refresh = getRefreshToken() ?: return

        val accounts = getSavedAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.userId == userId || it.email.equals(email, ignoreCase = true) }
        val account = SavedAccount(userId, name, email, access, refresh)
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }
        saveAccountsList(accounts)
    }

    fun getSavedAccounts(): List<SavedAccount> {
        val json = prefs?.getString(Constants.KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedAccount>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAccountsList(accounts: List<SavedAccount>) {
        val json = gson.toJson(accounts)
        prefs?.edit()?.putString(Constants.KEY_SAVED_ACCOUNTS, json)?.apply()
    }

    fun switchAccount(userId: String): Boolean {
        val account = getSavedAccounts().find { it.userId == userId } ?: return false
        prefs?.edit()
            ?.putString(Constants.KEY_USER_ID, account.userId)
            ?.putString(Constants.KEY_USER_NAME, account.name)
            ?.putString(Constants.KEY_USER_EMAIL, account.email)
            ?.putString(Constants.KEY_ACCESS_TOKEN, account.accessToken)
            ?.putString(Constants.KEY_REFRESH_TOKEN, account.refreshToken)
            ?.apply()
        return true
    }

    fun removeAccount(userId: String) {
        val accounts = getSavedAccounts().filterNot { it.userId == userId }
        saveAccountsList(accounts)
        if (getUserId() == userId) {
            if (accounts.isNotEmpty()) {
                switchAccount(accounts.first().userId)
            } else {
                clearCurrentSession()
            }
        }
    }

    fun clearCurrentSession() {
        prefs?.edit()
            ?.remove(Constants.KEY_ACCESS_TOKEN)
            ?.remove(Constants.KEY_REFRESH_TOKEN)
            ?.remove(Constants.KEY_USER_ID)
            ?.remove(Constants.KEY_USER_NAME)
            ?.remove(Constants.KEY_USER_EMAIL)
            ?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}

