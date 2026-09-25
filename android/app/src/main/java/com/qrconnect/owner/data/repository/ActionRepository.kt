package com.qrconnect.owner.data.repository

import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.ActionCreateRequest
import com.qrconnect.owner.data.model.ActionUpdateRequest
import com.qrconnect.owner.data.model.QrActionDto
import com.qrconnect.owner.util.NetworkResult

class ActionRepository {
    private val api = ApiClient.actionsApi

    suspend fun getActions(cardId: String): NetworkResult<List<QrActionDto>> {
        return try {
            val response = api.listActions(cardId)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to load actions: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun createAction(
        cardId: String,
        label: String,
        actionType: String
    ): NetworkResult<QrActionDto> {
        return try {
            val response = api.createAction(cardId, ActionCreateRequest(label, actionType))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to add action: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun toggleAction(actionId: String, enabled: Boolean): NetworkResult<QrActionDto> {
        return try {
            val response = api.updateAction(actionId, ActionUpdateRequest(enabled = enabled))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to update action: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun deleteAction(actionId: String): NetworkResult<Unit> {
        return try {
            val response = api.deleteAction(actionId)
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to delete action: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
