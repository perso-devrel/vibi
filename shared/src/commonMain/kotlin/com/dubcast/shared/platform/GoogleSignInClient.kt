package com.dubcast.shared.platform

/**
 * Google Sign-In 추상화. iOS 는 GoogleSignIn SDK (Swift bridge), Android 는 Credential
 * Manager (별도 phase) 로 구현. ID Token 만 받으면 충분 — 실제 검증과 access token
 * 발급은 BFF 가 담당.
 */
interface GoogleSignInClient {
    /** 성공 시 Google ID Token (JWT 문자열). 사용자가 취소했거나 실패 시 [Result.failure]. */
    suspend fun signIn(): Result<String>

    /** 로컬 GoogleSignIn 세션 정리. 토큰 저장소 삭제는 호출자(AuthRepository)가 별도 처리. */
    suspend fun signOut()
}

/**
 * Swift 가 구현해 K/N 으로 주입하는 callback 기반 brigde.
 *
 * suspend 함수를 Swift 가 직접 구현하기 어려워 (Kotlin → Swift async 는 자동이지만
 * 반대 방향은 wrapper 필요) callback 으로 단순화. iosMain 의 [com.dubcast.shared.platform.IosGoogleSignInClient]
 * 가 callback 을 [kotlinx.coroutines.suspendCancellableCoroutine] 으로 suspend 화.
 */
interface GoogleSignInBridge {
    fun signIn(callback: (idToken: String?, errorMessage: String?) -> Unit)
    fun signOut()
}
