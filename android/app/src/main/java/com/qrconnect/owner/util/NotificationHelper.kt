package com.qrconnect.owner.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.qrconnect.owner.data.model.EventDto
import com.qrconnect.owner.ui.events.EventsActivity

object NotificationHelper {
    const val CHANNEL_ID = "qr_connect_alerts"
    const val CHANNEL_NAME = "QR Connect Activity Alerts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts, visitor messages, and interactions from QR Connect cards"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showEventNotification(context: Context, event: EventDto) {
        createNotificationChannel(context)

        val cardTitle = if (!event.cardName.isNullOrBlank()) {
            "Activity on ${event.cardName}"
        } else {
            "QR Connect Activity"
        }

        val (iconEmoji, bodyText) = when (event.type.lowercase()) {
            "photo" -> {
                val caption = event.content?.substringAfter(":::", "")?.ifBlank { null }
                "📸" to (caption?.let { "Photo received: $it" } ?: "Visitor sent a photo note.")
            }
            "video" -> {
                val caption = event.content?.substringAfter(":::", "")?.ifBlank { null }
                "🎥" to (caption?.let { "Video received: $it" } ?: "Visitor sent a video note.")
            }
            "voice" -> "🎙️" to "Visitor recorded a voice note."
            "alert" -> "🚨" to (event.content ?: "Urgent alert triggered!")
            "message" -> "💬" to (event.content ?: "New message from visitor.")
            "location" -> "📍" to (event.address ?: "Visitor shared GPS coordinates.")
            "scan" -> "👀" to "Someone scanned your QR code."
            else -> "⚡" to (event.content ?: "New interaction received.")
        }

        val fullTitle = "$iconEmoji $cardTitle"
        showNotification(context, fullTitle, bodyText, event.cardId)
    }

    fun showNotification(context: Context, title: String, body: String, cardId: String? = null) {
        createNotificationChannel(context)

        val intent = Intent(context, EventsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!cardId.isNullOrBlank()) {
                putExtra(EventsActivity.EXTRA_CARD_ID, cardId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notificationBuilder.build())
    }
}
