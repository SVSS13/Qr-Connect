package com.qrconnect.owner.data.repository

import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.CardCreateRequest
import com.qrconnect.owner.data.model.CardUpdateRequest
import com.qrconnect.owner.data.model.QrCardDto
import com.qrconnect.owner.util.NetworkResult

class CardRepository {
    private val api = ApiClient.cardsApi

    suspend fun getCards(): NetworkResult<List<QrCardDto>> {
        return try {
            val response = api.listCards()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to fetch cards: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun createCard(name: String, type: String): NetworkResult<QrCardDto> {
        return try {
            val response = api.createCard(CardCreateRequest(name, type))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("Failed to create card: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun deleteCard(cardId: String): NetworkResult<Unit> {
        return try {
            val response = api.deleteCard(cardId)
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to delete card: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
