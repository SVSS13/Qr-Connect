package com.qrconnect.owner.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrconnect.owner.data.model.AuthResponse
import com.qrconnect.owner.data.repository.AuthRepository
import com.qrconnect.owner.util.NetworkResult
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    private val _loginState = MutableLiveData<NetworkResult<AuthResponse>>()
    val loginState: LiveData<NetworkResult<AuthResponse>> = _loginState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = NetworkResult.Error("Email and password cannot be empty")
            return
        }

        _loginState.value = NetworkResult.Loading
        viewModelScope.launch {
            _loginState.value = repository.login(email, password)
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _loginState.value = NetworkResult.Error("All fields are required")
            return
        }
        if (password.length < 8) {
            _loginState.value = NetworkResult.Error("Password must be at least 8 characters")
            return
        }

        _loginState.value = NetworkResult.Loading
        viewModelScope.launch {
            _loginState.value = repository.register(name, email, password)
        }
    }

    fun isLoggedIn(): Boolean = repository.isLoggedIn()
}
