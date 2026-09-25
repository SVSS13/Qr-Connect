package com.qrconnect.owner.data.api

import com.qrconnect.owner.data.model.CardCreateRequest
import com.qrconnect.owner.data.model.CardUpdateRequest
import com.qrconnect.owner.data.model.QrCardDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CardsApi {
    @GET("api/owner/cards")
    suspend fun listCards(): Response<List<QrCardDto>>

    @POST("api/owner/cards")
    suspend fun createCard(@Body request: CardCreateRequest): Response<QrCardDto>

    @PATCH("api/owner/cards/{id}")
    suspend fun updateCard(
        @Path("id") id: String,
        @Body request: CardUpdateRequest
    ): Response<QrCardDto>

    @DELETE("api/owner/cards/{id}")
    suspend fun deleteCard(@Path("id") id: String): Response<Unit>
}
