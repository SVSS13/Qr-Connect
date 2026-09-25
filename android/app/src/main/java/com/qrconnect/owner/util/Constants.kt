package com.qrconnect.owner.util

object Constants {
    // Using http://localhost:8000/ with `adb reverse tcp:8000 tcp:8000` enables
    // direct zero-config USB connection from physical device to host backend server.
    const val DEFAULT_BASE_URL = "http://localhost:8000/"
    const val PREFS_NAME = "qr_connect_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_ID = "user_id"
    const val KEY_FCM_TOKEN = "fcm_token"
    const val KEY_SAVED_ACCOUNTS = "saved_accounts"

    const val KEY_QR_HOST_MODE = "qr_host_mode"
    const val KEY_CUSTOM_QR_HOST = "custom_qr_host"

    const val MODE_DEV_LOCALHOST = "localhost"
    const val MODE_PROD_RENDER = "render"
    const val MODE_PROD_VANITY = "vanity"

    const val URL_DEV_LOCALHOST = "http://localhost:8000/q/"
    const val URL_PROD_RENDER = "https://qr-connect.onrender.com/q/"
    const val URL_PROD_VANITY = "https://qrconnect.me/q/"
}

