package com.dubcast.shared.platform

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class IosGoogleSignInClient(
    private val bridge: GoogleSignInBridge,
) : GoogleSignInClient {

    override suspend fun signIn(): Result<String> = suspendCancellableCoroutine { cont ->
        bridge.signIn { idToken, errorMessage ->
            val result = if (idToken != null) {
                Result.success(idToken)
            } else {
                Result.failure(RuntimeException(errorMessage ?: "google_sign_in_failed"))
            }
            if (cont.isActive) cont.resume(result)
        }
    }

    override suspend fun signOut() {
        bridge.signOut()
    }
}
