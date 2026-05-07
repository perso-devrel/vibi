package com.dubcast.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dubcast.shared.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data object Success : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun signIn() {
        if (_state.value is UiState.Loading) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogle().fold(
                onSuccess = { _state.value = UiState.Success },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Sign-in failed") },
            )
        }
    }
}
