package com.vibi.shared.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 세션 만료(재인증 필요) 신호를 UI 로 전달하는 앱 전역 이벤트 버스.
 *
 * refresh 토큰이 없어([com.vibi.shared.data.remote.SessionExpiredException] 참조) 401 = 재로그인만이
 * 유일한 복구다. HTTP 레이어([com.vibi.shared.data.remote.createBffHttpClient])가 인증 요청의 401 을
 * 포착하면 [notifySessionExpired] 를 호출하고, 내비게이션(VibiNavHost)이 이를 구독해 로그인 화면으로
 * 라우팅한다. VibiNavHost 루트 collector 가 최초 컴포지션부터 상시 구독하고 401 은 네트워크 왕복 뒤에
 * 오므로 이벤트 유실이 없다. extraBufferCapacity=1 은 그럼에도 non-suspend HTTP 콜백의 tryEmit 이
 * (collector 부재 순간에도) false 를 반환하지 않게 하는 버퍼.
 */
class AuthEventBus {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun notifySessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
