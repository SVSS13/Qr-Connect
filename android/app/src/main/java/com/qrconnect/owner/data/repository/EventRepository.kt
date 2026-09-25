package com.qrconnect.owner.data.repository

import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.EventDto
import com.qrconnect.owner.util.NetworkResult

class EventRepository {
    private val api = ApiClient.eventsApi

    suspend fun getEvents(cardId: String? = null): NetworkResult<List<EventDto>> {
        return try {
            val response = api.listEvents(cardId = cardId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to load events: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
