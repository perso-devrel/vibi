package com.dubcast.shared.platform

/**
 * Android 구현은 후속 phase 에서 Credential Manager + GoogleIdOption 으로 작성.
 * v1 은 iOS 우선 배포라 컴파일만 통과시키는 stub — 실제 호출되면 즉시 실패.
 */
class AndroidGoogleSignInClient : GoogleSignInClient {
    override suspend fun signIn(): Result<String> =
        Result.failure(NotImplementedError("Android Google Sign-In not yet implemented"))

    override suspend fun signOut() {
        // no-op
    }
}
