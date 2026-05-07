package com.dubcast.shared.data.local

import com.dubcast.shared.platform.currentTimeMillis
import com.russhwolf.settings.Settings

/**
 * BFF 가 발급한 access token (JWT) 을 저장. 만료시간 (epoch ms) 을 함께 저장해서
 * 매 호출마다 JWT 디코딩 없이 빠르게 유효성 판단.
 *
 * v1 은 평문 저장 (NSUserDefaults / SharedPreferences) — Keychain / EncryptedSharedPreferences
 * 도입은 v2.
 */
class AuthTokenStore(private val settings: Settings) {

    fun saveToken(jwt: String, expiresAt: Long) {
        settings.putString(KEY_TOKEN, jwt)
        settings.putLong(KEY_EXP, expiresAt)
    }

    /** 만료된 토큰은 자동 폐기 후 null. */
    fun getValidToken(): String? {
        val exp = settings.getLongOrNull(KEY_EXP) ?: return null
        if (exp <= currentTimeMillis()) {
            clear()
            return null
        }
        return settings.getStringOrNull(KEY_TOKEN)
    }

    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_EXP)
    }

    companion object {
        private const val KEY_TOKEN = "auth.jwt"
        private const val KEY_EXP = "auth.exp"
    }
}
