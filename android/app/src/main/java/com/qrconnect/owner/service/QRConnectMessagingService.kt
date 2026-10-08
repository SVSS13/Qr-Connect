package com.qrconnect.owner.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qrconnect.owner.R
import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.FcmTokenRequest
import com.qrconnect.owner.ui.events.EventsActivity
import com.qrconnect.owner.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QRConnectMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "QRConnectFCM"
        const val CHANNEL_ID = "qr_connect_alerts"
        const val CHANNEL_NAME = "QR Connect Activity Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Registration Token: $token")
        TokenManager.saveFcmToken(token)

        // If user is currently logged in, sync token with backend
        if (TokenManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.authApi.registerFcmToken(FcmTokenRequest(token))
                    Log.d(TAG, "Successfully registered FCM token with backend")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register FCM token with backend", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["card_name"]?.let { "Activity on $it" }
            ?: "QR Connect Notification"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["event_type"]?.let { "New $it received" }
            ?: "Someone interacted with your QR card."

        val cardId = remoteMessage.data["card_id"]

        com.qrconnect.owner.util.NotificationHelper.showNotification(this, title, body, cardId)
    }
}
