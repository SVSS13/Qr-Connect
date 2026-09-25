package com.qrconnect.owner.data.api

import com.qrconnect.owner.data.model.ActionCreateRequest
import com.qrconnect.owner.data.model.ActionUpdateRequest
import com.qrconnect.owner.data.model.QrActionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ActionsApi {
    @GET("api/owner/cards/{cardId}/actions")
    suspend fun listActions(@Path("cardId") cardId: String): Response<List<QrActionDto>>

    @POST("api/owner/cards/{cardId}/actions")
    suspend fun createAction(
        @Path("cardId") cardId: String,
        @Body request: ActionCreateRequest
    ): Response<QrActionDto>

    @PATCH("api/owner/actions/{id}")
    suspend fun updateAction(
        @Path("id") id: String,
        @Body request: ActionUpdateRequest
    ): Response<QrActionDto>

    @DELETE("api/owner/actions/{id}")
    suspend fun deleteAction(@Path("id") id: String): Response<Unit>
}
