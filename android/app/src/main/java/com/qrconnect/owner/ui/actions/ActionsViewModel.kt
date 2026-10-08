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

    private val _operationResult = MutableLiveData<NetworkResult<String>?>()
    val operationResult: LiveData<NetworkResult<String>?> = _operationResult

    fun clearOperationResult() {
        _operationResult.value = null
    }

    fun fetchActions(cardId: String) {
        _actions.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = repository.getActions(cardId)
            if (result is NetworkResult.Success) {
                _actions.value = NetworkResult.Success(result.data.toList())
            } else {
                _actions.value = result
            }
        }
    }

    fun createAction(cardId: String, label: String, actionType: String) {
        _operationResult.value = NetworkResult.Loading
        viewModelScope.launch {
            when (val result = repository.createAction(cardId, label, actionType)) {
                is NetworkResult.Success -> {
                    _operationResult.value = NetworkResult.Success("Action '${result.data.label}' added successfully!")
                    fetchActions(cardId)
                }
                is NetworkResult.Error -> {
                    _operationResult.value = NetworkResult.Error(result.message)
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun toggleAction(cardId: String, actionId: String, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = repository.toggleAction(actionId, enabled)) {
                is NetworkResult.Success -> {
                    val statusText = if (enabled) "enabled" else "disabled"
                    _operationResult.value = NetworkResult.Success("Action $statusText")
                    fetchActions(cardId)
                }
                is NetworkResult.Error -> {
                    _operationResult.value = NetworkResult.Error(result.message)
                    fetchActions(cardId) // Revert UI
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun deleteAction(cardId: String, actionId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteAction(actionId)) {
                is NetworkResult.Success -> {
                    _operationResult.value = NetworkResult.Success("Action deleted successfully")
                    fetchActions(cardId)
                }
                is NetworkResult.Error -> {
                    _operationResult.value = NetworkResult.Error(result.message)
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }
}
