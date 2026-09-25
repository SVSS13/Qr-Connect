package com.qrconnect.owner.ui.actions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrconnect.owner.data.model.QrActionDto
import com.qrconnect.owner.data.repository.ActionRepository
import com.qrconnect.owner.util.NetworkResult
import kotlinx.coroutines.launch

class ActionsViewModel(private val repository: ActionRepository = ActionRepository()) : ViewModel() {
    private val _actions = MutableLiveData<NetworkResult<List<QrActionDto>>>()
    val actions: LiveData<NetworkResult<List<QrActionDto>>> = _actions

    fun fetchActions(cardId: String) {
        _actions.value = NetworkResult.Loading
        viewModelScope.launch {
            _actions.value = repository.getActions(cardId)
        }
    }

    fun createAction(cardId: String, label: String, actionType: String) {
        viewModelScope.launch {
            val result = repository.createAction(cardId, label, actionType)
            if (result is NetworkResult.Success) {
                fetchActions(cardId)
            }
        }
    }

    fun toggleAction(cardId: String, actionId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAction(actionId, enabled)
            fetchActions(cardId)
        }
    }

    fun deleteAction(cardId: String, actionId: String) {
        viewModelScope.launch {
            repository.deleteAction(actionId)
            fetchActions(cardId)
        }
    }
}
