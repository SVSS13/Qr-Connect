package com.qrconnect.owner.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CardCreateRequest(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String = "other"
)

data class CardUpdateRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("status") val status: String? = null
)

data class QrCardDto(
    @SerializedName("id") val id: String,
    @SerializedName("owner_id") val ownerId: String,
    @SerializedName("card_token") val cardToken: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("rotated_at") val rotatedAt: String? = null
) : Serializable
