package com.qrconnect.owner.util

import android.content.Context
import android.util.Log
import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.EventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EventSyncManager {
    private const val TAG = "EventSyncManager"
    private const val PREFS_NAME = "qrconnect_event_sync"
    private const val KEY_SEEN_IDS = "seen_event_ids"
    private const val KEY_INITIALIZED = "sync_initialized"

    private val seenEventIds = mutableSetOf<String>()
    private var isInitialized = false

    // Listeners for UI updates (e.g., DashboardActivity)
    var onNewEventsListener: ((List<EventDto>) -> Unit)? = null
    var onUnreadCountChanged: ((Int) -> Unit)? = null

    private fun loadSeenIds(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()
        seenEventIds.addAll(saved)
        isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
    }

    private fun saveSeenIds(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Keep up to 300 most recent IDs
        val truncatedSet = if (seenEventIds.size > 300) {
            seenEventIds.toList().takeLast(300).toSet()
        } else {
            seenEventIds
        }
        prefs.edit()
            .putStringSet(KEY_SEEN_IDS, truncatedSet)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    suspend fun syncEvents(context: Context, isBackground: Boolean = false): List<EventDto> =
        withContext(Dispatchers.IO) {
            if (!TokenManager.isLoggedIn()) {
                return@withContext emptyList<EventDto>()
            }

            loadSeenIds(context)

            try {
                val response = ApiClient.eventsApi.listEvents()
                if (response.isSuccessful && response.body() != null) {
                    val events = response.body()!!

                    // On first launch or fresh login, mark all existing events as seen
                    if (!isInitialized) {
                        seenEventIds.addAll(events.map { it.id })
                        isInitialized = true
                        saveSeenIds(context)
                        withContext(Dispatchers.Main) {
                            onUnreadCountChanged?.invoke(0)
                        }
                        return@withContext emptyList<EventDto>()
                    }

                    // Find unseen events (reverse order so oldest new event alerts first)
                    val newEvents = events.filter { it.id !in seenEventIds }.reversed()

                    if (newEvents.isNotEmpty()) {
                        Log.i(TAG, "Detected ${newEvents.size} new events!")
                        for (event in newEvents) {
                            seenEventIds.add(event.id)
                            NotificationHelper.showEventNotification(context, event)
                        }
                        saveSeenIds(context)

                        withContext(Dispatchers.Main) {
                            onNewEventsListener?.invoke(newEvents)
                            onUnreadCountChanged?.invoke(newEvents.size)
                        }
                    }

                    return@withContext newEvents
                } else {
                    Log.w(TAG, "Events fetch failed with HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception during event sync: ${e.message}")
            }

            return@withContext emptyList<EventDto>()
        }

    fun markAllAsRead(context: Context) {
        saveSeenIds(context)
        onUnreadCountChanged?.invoke(0)
    }
}
