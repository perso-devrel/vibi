package com.dubcast.shared.data.repository

import com.dubcast.shared.data.local.AuthTokenStore
import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.domain.model.AuthUser
import com.dubcast.shared.platform.GoogleSignInClient

/**
 * Google OAuth → BFF JWT 교환 + 로컬 토큰 캐시. v1 은 인터페이스 분리 없이 단일 클래스.
 *
 * - [signInWithGoogle] — native SDK 로 Google ID Token → BFF /auth/google → JWT 저장
 * - [hasValidSession] — 저장된 JWT 가 만료 안 됐는지 (스플래시에서 라우팅 결정용)
 * - [signOut] — Google 세션 + 토큰 캐시 모두 정리
 */
class AuthRepository(
    private val signInClient: GoogleSignInClient,
    private val bffApi: BffApi,
    private val tokenStore: AuthTokenStore,
) {
    suspend fun signInWithGoogle(): Result<AuthUser> = runCatching {
        val idToken = signInClient.signIn().getOrThrow()
        val resp = bffApi.exchangeGoogleIdToken(idToken)
        tokenStore.saveToken(resp.accessToken, resp.expiresAt)
        resp.user.toDomain()
    }

    fun hasValidSession(): Boolean = tokenStore.getValidToken() != null

    suspend fun signOut() {
        // 토큰 먼저 삭제 — native SDK signOut 이 throw 해도 로컬 세션은 끊긴 상태로 남도록.
        tokenStore.clear()
        runCatching { signInClient.signOut() }
    }
}
