package com.qrconnect.owner.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ActionCreateRequest(
    @SerializedName("label") val label: String,
    @SerializedName("action_type") val actionType: String,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("config") val config: Map<String, Any>? = null
)

data class ActionUpdateRequest(
    @SerializedName("label") val label: String? = null,
    @SerializedName("action_type") val actionType: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    @SerializedName("config") val config: Map<String, Any>? = null,
    @SerializedName("enabled") val enabled: Boolean? = null
)

data class QrActionDto(
    @SerializedName("id") val id: String,
    @SerializedName("qr_card_id") val qrCardId: String,
    @SerializedName("label") val label: String,
    @SerializedName("action_type") val actionType: String,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("config") val config: Map<String, Any>? = null,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("created_at") val createdAt: String
) : Serializable
