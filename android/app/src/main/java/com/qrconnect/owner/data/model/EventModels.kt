package com.qrconnect.owner.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class EventDto(
    @SerializedName("id") val id: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("action_id") val actionId: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("card_name") val cardName: String? = null,
    @SerializedName("card_id") val cardId: String? = null,
    @SerializedName("created_at") val createdAt: String
) : Serializable
