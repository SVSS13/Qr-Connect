package com.qrconnect.owner.data.api

import com.qrconnect.owner.data.model.EventDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface EventsApi {
    @GET("api/owner/events")
    suspend fun listEvents(
        @Query("card_id") cardId: String? = null,
        @Query("type") type: String? = null
    ): Response<List<EventDto>>
}
