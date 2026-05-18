package com.vibi.shared.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.data.local.AuthTokenStore
import com.vibi.shared.data.local.CreditStore
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.dto.CreditPurchaseRequest
import com.vibi.shared.data.repository.AuthRepository
import com.vibi.shared.domain.model.AuthUser
import com.vibi.shared.domain.model.IapPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 홈화면 우상단 유저 메뉴를 위한 ViewModel.
 *
 * 잔액 동기화 (`refreshBalance`) 는 메뉴가 열릴 때만 호출 — InputScreen 재진입 마다
 * BFF round-trip 을 발생시키지 않기 위함. 회원탈퇴 후 user-scoped Room row 는
 * UserSession.userId 변경으로 자동 격리되므로 별도 wipe 하지 않는다 (signOut 동작과 동일).
 */
class UserMenuViewModel(
    private val authRepository: AuthRepository,
    tokenStore: AuthTokenStore,
    private val creditStore: CreditStore,
    private val userSession: UserSession,
    private val bffApi: BffApi,
) : ViewModel() {

    data class UiState(
        val user: AuthUser?,
        val credits: Int,
    ) {
        val isAdmin: Boolean get() = user?.isAdmin == true
    }

    val uiState: StateFlow<UiState> = combine(
        tokenStore.cachedUser,
        creditStore.balance,
    ) { user, credits -> UiState(user = user, credits = credits) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(
                user = tokenStore.cachedUser.value,
                credits = creditStore.balance.value,
            )
        )

    val products: List<CreditProduct> = CreditProduct.DEFAULTS

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    fun refreshBalance() {
        viewModelScope.launch {
            runCatching { bffApi.getCreditBalance() }
                .onSuccess { resp -> creditStore.setBalance(userSession.current(), resp.balance) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _navigateToLogin.emit(Unit)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            runCatching { authRepository.deleteAccount() }
            _navigateToLogin.emit(Unit)
        }
    }

    /** IAP 시스템이 결제 성공 콜백을 돌려준 직후 호출 — BFF 가 영수증 검증 + idempotent 가산. */
    fun purchaseCredits(
        product: CreditProduct,
        platform: IapPlatform,
        receipt: String,
        transactionId: String,
    ) {
        viewModelScope.launch {
            val req = CreditPurchaseRequest(
                productId = product.productId,
                platform = platform.wireName,
                receipt = receipt,
                transactionId = transactionId,
            )
            runCatching { bffApi.purchaseCredits(req) }
                .onSuccess { resp -> creditStore.setBalance(userSession.current(), resp.balance) }
                .onFailure { refreshBalance() }
        }
    }

    /**
     * 관리자 무료 충전. 매 호출마다 새 txId 라 BFF 가 새 grant 로 처리 — 관리자가 같은 상품을
     * 반복 탭하면 매번 가산되는 동작이 의도.
     *
     * **출시 전 TODO**: BFF 가 진짜 receipt 검증 도입 시 `"admin-grant"` synthetic receipt 가
     * 거부됨. 그 시점에 BFF `POST /credits/admin-grant` (requireAdmin) 추가 + 본 함수가 그쪽으로 분기.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun adminGrantCredits(product: CreditProduct): Result<Unit> {
        check(uiState.value.isAdmin) { "adminGrantCredits called by non-admin user" }
        val req = CreditPurchaseRequest(
            productId = product.productId,
            platform = IapPlatform.APPLE.wireName,
            receipt = "admin-grant",
            transactionId = "admin-${product.productId}-${Uuid.random()}",
        )
        return runCatching { bffApi.purchaseCredits(req) }
            .onSuccess { resp -> creditStore.setBalance(userSession.current(), resp.balance) }
            .onFailure { refreshBalance() }
            .map { Unit }
    }
}

/**
 * 크레딧 상품 한 건. 실제 SKU 와 매핑되는 [productId] + UI 표시용 [credits]/[priceLabel].
 */
data class CreditProduct(
    val productId: String,
    val credits: Int,
    val priceLabel: String,
    val title: String,
    val subtitle: String? = null,
    val highlight: Boolean = false,
) {
    companion object {
        val DEFAULTS: List<CreditProduct> = listOf(
            CreditProduct(
                productId = "vibi.credits.10",
                credits = 10,
                priceLabel = "₩1,500",
                title = "10 크레딧",
                subtitle = "가볍게 한 번 더",
            ),
            CreditProduct(
                productId = "vibi.credits.50",
                credits = 50,
                priceLabel = "₩6,900",
                title = "50 크레딧",
                subtitle = "가장 인기",
                highlight = true,
            ),
            CreditProduct(
                productId = "vibi.credits.150",
                credits = 150,
                priceLabel = "₩18,000",
                title = "150 크레딧",
                subtitle = "1팩당 약 17% 저렴",
            ),
        )
    }
}
