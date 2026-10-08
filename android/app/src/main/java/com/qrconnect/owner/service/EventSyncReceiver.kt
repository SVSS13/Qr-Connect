package com.qrconnect.owner.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.qrconnect.owner.util.EventSyncManager
import com.qrconnect.owner.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventSyncReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EventSyncReceiver"
        private const val REQUEST_CODE = 4001
        private const val INTERVAL_MILLIS = 30_000L // 30 seconds background sync

        fun schedulePeriodicSync(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, EventSyncReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = System.currentTimeMillis() + INTERVAL_MILLIS
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not schedule alarm: ${e.message}")
            }
        }

        fun cancelPeriodicSync(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, EventSyncReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!TokenManager.isLoggedIn()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EventSyncManager.syncEvents(context, isBackground = true)
            } catch (e: Exception) {
                Log.w(TAG, "Background sync failed: ${e.message}")
            } finally {
                // Re-schedule next sync
                schedulePeriodicSync(context)
                pendingResult.finish()
            }
        }
    }
}
