package com.qrconnect.owner.ui.events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrconnect.owner.data.model.EventDto
import com.qrconnect.owner.data.repository.EventRepository
import com.qrconnect.owner.util.NetworkResult
import kotlinx.coroutines.launch

class EventsViewModel(private val repository: EventRepository = EventRepository()) : ViewModel() {
    private val _events = MutableLiveData<NetworkResult<List<EventDto>>>()
    val events: LiveData<NetworkResult<List<EventDto>>> = _events

    fun fetchEvents(cardId: String? = null) {
        _events.value = NetworkResult.Loading
        viewModelScope.launch {
            _events.value = repository.getEvents(cardId)
        }
    }
}
