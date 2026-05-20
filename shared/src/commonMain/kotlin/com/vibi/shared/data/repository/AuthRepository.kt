package com.vibi.shared.data.repository

import com.vibi.shared.data.local.AuthTokenStore
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.local.extractJwtSubject
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.dto.AuthResponseDto
import com.vibi.shared.domain.model.AuthUser
import com.vibi.shared.platform.AppleSignInClient
import com.vibi.shared.platform.GoogleSignInClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google / Apple OAuth → BFF JWT 교환 + 로컬 토큰 캐시 + 계정별 로컬 데이터 스코핑.
 *
 * - [signInWithGoogle] / [signInWithApple] — native SDK 로 ID Token (+ Apple 의 fullName)
 *   확보 → BFF `/auth/{provider}` → JWT 저장 + [UserSession] 갱신. JWT 의 sub 는 BFF
 *   internal UUID — Google sub / Apple sub 가 아니라 user 테이블 PK.
 *   row 자체는 userId 컬럼으로 scoped — 다른 계정 row 를 wipe 하지 않고도 UI query
 *   (`observeAllForUser`) 에서 격리되므로 A↔B 왕복 시 각자 작업 누적 유지.
 * - [hasValidSession] — 저장된 JWT 가 만료 안 됐는지 (스플래시에서 라우팅 결정용).
 * - [restoreSession] — 앱 시작 시 저장된 JWT 의 sub 를 [UserSession] 으로 복원.
 * - [signOut] — Google / Apple native 세션 + 토큰 캐시 정리. 로컬 데이터는 보존.
 */
class AuthRepository(
    private val googleSignInClient: GoogleSignInClient,
    private val appleSignInClient: AppleSignInClient,
    private val bffApi: BffApi,
    private val tokenStore: AuthTokenStore,
    private val userSession: UserSession,
) {
    suspend fun signInWithGoogle(): Result<AuthUser> = runCatching {
        val idToken = googleSignInClient.signIn().getOrThrow()
        val resp = bffApi.exchangeGoogleIdToken(idToken)
        finalizeSession(resp)
        resp.user.toDomain().also { tokenStore.saveUser(it) }
    }

    suspend fun signInWithApple(): Result<AuthUser> = runCatching {
        val payload = appleSignInClient.signIn().getOrThrow()
        val resp = bffApi.exchangeAppleIdToken(payload.idToken, payload.fullName)
        finalizeSession(resp)
        resp.user.toDomain().also { tokenStore.saveUser(it) }
    }

    fun hasValidSession(): Boolean = tokenStore.getValidToken() != null

    /**
     * 앱 시작 시 토큰의 sub 또는 lastUserId 로 [UserSession] 복원.
     * Base64 decode + JSON 파싱 + Settings I/O 가 동기 작업이라 caller (Splash) 가 Main 이면
     * 첫 frame block. Dispatchers.Default 로 분리.
     */
    suspend fun restoreSession() = withContext(Dispatchers.Default) {
        val sub = tokenStore.getValidToken()?.let(::extractJwtSubject)
        val resolved = sub ?: tokenStore.lastUserId() ?: UserSession.ANONYMOUS_USER_ID
        userSession.set(resolved)
    }

    suspend fun signOut() {
        // 로컬 row 는 보존 — 같은 계정 재로그인 시 "이어서 작업" 복원.
        // 다른 계정 세션에서는 row 의 userId 컬럼 매칭으로 자동 격리되므로 wipe 불필요.
        // 토큰 먼저 삭제 — native SDK signOut 이 throw 해도 로컬 세션은 끊긴 상태로 남도록.
        tokenStore.clear()
        userSession.reset()
        runCatching { googleSignInClient.signOut() }
        runCatching { appleSignInClient.signOut() }
    }

    /**
     * 회원탈퇴 — BFF `DELETE /auth/account` 호출 + 로컬 토큰/세션 정리.
     *
     * 호출자는 로컬 user-scoped row 정리 (EditProject 등) 를 별도로 진행한다 ([UserMenuViewModel]
     * 의 deleteAccount 흐름). BFF 호출이 401/네트워크 실패해도 로컬 정리는 계속 진행되어야
     * 다음 부팅 때 stale 세션으로 튕기지 않는다 — 그래서 finally 블록에서 signOut 호출.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        try {
            bffApi.deleteAccount()
        } finally {
            signOut()
        }
    }

    private fun finalizeSession(resp: AuthResponseDto) {
        tokenStore.saveToken(resp.accessToken, resp.expiresAt)
        val newUserId = extractJwtSubject(resp.accessToken) ?: resp.user.sub
        switchUser(newUserId)
    }

    private fun switchUser(newUserId: String) {
        // wipe 없음 — A→B→A 왕복 시 각자 작업 누적 유지. UI 격리는 user-scoped query 가 처리.
        tokenStore.saveLastUserId(newUserId)
        userSession.set(newUserId)
    }
}
