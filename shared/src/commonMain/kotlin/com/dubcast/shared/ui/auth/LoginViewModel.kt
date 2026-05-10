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
        // [TEMP] 시연용 Google Sign-In 우회 — 즉시 Success. BFF 는 인증 미들웨어 없어 우회 안전.
        // 복구: 즉시 Success 라인 제거하고 viewModelScope.launch 블록 주석 해제.
        _state.value = UiState.Success
        // _state.value = UiState.Loading
        // viewModelScope.launch {
        //     authRepository.signInWithGoogle().fold(
        //         onSuccess = { _state.value = UiState.Success },
        //         onFailure = { e -> _state.value = UiState.Error(e.message ?: "Sign-in failed") },
        //     )
        // }
    }
}
