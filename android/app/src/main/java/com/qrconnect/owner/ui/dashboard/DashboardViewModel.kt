package com.qrconnect.owner.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrconnect.owner.data.model.QrCardDto
import com.qrconnect.owner.data.repository.CardRepository
import com.qrconnect.owner.util.NetworkResult
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {
    private val _cards = MutableLiveData<NetworkResult<List<QrCardDto>>>()
    val cards: LiveData<NetworkResult<List<QrCardDto>>> = _cards

    private val _createCardResult = MutableLiveData<NetworkResult<QrCardDto>?>()
    val createCardResult: LiveData<NetworkResult<QrCardDto>?> = _createCardResult

    fun fetchCards() {
        _cards.value = NetworkResult.Loading
        viewModelScope.launch {
            _cards.value = repository.getCards()
        }
    }

    fun createCard(name: String, type: String) {
        _createCardResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = repository.createCard(name, type)
            _createCardResult.value = result
            if (result is NetworkResult.Success) {
                fetchCards()
            }
        }
    }

    fun resetCreateResult() {
        _createCardResult.value = null
    }
}
