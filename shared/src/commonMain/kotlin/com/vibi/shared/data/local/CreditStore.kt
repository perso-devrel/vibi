package com.vibi.shared.data.local

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 사용자별 크레딧 잔액 **로컬 캐시**.
 *
 * BFF (`/api/v2/credits`) 가 source of truth — 본 store 는 마지막으로 받은 값을
 * 다음 부팅 / 화면 진입 직후 instant 표시하기 위해 평문 저장 (Multiplatform Settings).
 * 실제 갱신은 [UserMenuViewModel] 이 BFF 호출 후 [setBalance] 를 호출해 반영한다.
 *
 * 키는 user-scoped: `credits.<userId>` — A/B 계정 왕복 시 각자 잔액 격리.
 */
class CreditStore(
    private val settings: Settings,
    userSession: UserSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _balance = MutableStateFlow(read(userSession.current()))
    val balance: StateFlow<Int> = _balance.asStateFlow()

    init {
        // userId 가 바뀌면 그 계정의 캐시 값으로 swap (BFF 호출 전까지의 stale-while-revalidate).
        userSession.userId
            .onEach { _balance.value = read(it) }
            .launchIn(scope)
    }

    /** BFF 응답 등 외부 권위로부터 받은 값을 그대로 set. */
    fun setBalance(userId: String, balance: Int) {
        val normalized = balance.coerceAtLeast(0)
        // persisted 값만 보고 early-return 하면 _balance(StateFlow) 가 다른 이유로 drift 한 경우
        // (userId swap 등) stale 인 채로 남는다. putInt 는 idempotent 하고 emit 은 아래에서 guard 하므로
        // 항상 둘 다 반영.
        settings.putInt(keyFor(userId), normalized)
        if (_balance.value != normalized) _balance.value = normalized
    }

    private fun read(userId: String): Int =
        settings.getIntOrNull(keyFor(userId)) ?: 0

    private fun keyFor(userId: String): String = "credits.$userId"
}
