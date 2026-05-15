package com.vibi.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    enum class Provider { Google, Apple }

    sealed interface UiState {
        data object Idle : UiState
        data class Loading(val provider: Provider) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // success 는 sticky state 가 아니라 일회성 navigation 이벤트.
    // ViewModelStoreOwner 캐시 (Activity 단일 store) 때문에 로그아웃 후 LoginScreen 재진입 시
    // 이전 Success state 가 그대로 남아 즉시 홈으로 튕기던 버그 방지.
    private val _navigateToHome = MutableSharedFlow<Unit>()
    val navigateToHome: SharedFlow<Unit> = _navigateToHome.asSharedFlow()

    fun signInWithGoogle() = runProvider(Provider.Google) { authRepository.signInWithGoogle() }

    fun signInWithApple() = runProvider(Provider.Apple) { authRepository.signInWithApple() }

    private fun <T> runProvider(provider: Provider, block: suspend () -> Result<T>) {
        if (_state.value is UiState.Loading) return
        _state.value = UiState.Loading(provider)
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    _state.value = UiState.Idle
                    _navigateToHome.emit(Unit)
                },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Sign-in failed") },
            )
        }
    }
}
