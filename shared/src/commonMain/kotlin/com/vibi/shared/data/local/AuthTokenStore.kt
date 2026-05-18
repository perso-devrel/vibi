package com.vibi.shared.data.local

import com.vibi.shared.domain.model.AuthUser
import com.vibi.shared.platform.currentTimeMillis
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BFF 가 발급한 access token (JWT) 을 저장. 만료시간 (epoch ms) 을 함께 저장해서
 * 매 호출마다 JWT 디코딩 없이 빠르게 유효성 판단.
 *
 * v1 은 평문 저장 (NSUserDefaults / SharedPreferences) — Keychain / EncryptedSharedPreferences
 * 도입은 v2.
 *
 * 또한 마지막 로그인 시점의 [AuthUser] (name/email/picture) 도 함께 캐시한다 — 메뉴에
 * 표시할 프로필을 BFF round-trip 없이 즉시 보여주기 위해.
 */
class AuthTokenStore(private val settings: Settings) {

    private val _cachedUser = MutableStateFlow<AuthUser?>(readCachedUser())
    /** 마지막 로그인 응답의 user. 로그아웃 시 null. */
    val cachedUser: StateFlow<AuthUser?> = _cachedUser.asStateFlow()

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
        USER_KEYS.forEach(settings::remove)
        _cachedUser.value = null
    }

    /** 직전에 로그인했던 userId (계정 전환 감지용). 로그아웃 후에도 유지. */
    fun lastUserId(): String? = settings.getStringOrNull(KEY_LAST_USER_ID)

    fun saveLastUserId(userId: String) {
        if (lastUserId() == userId) return
        settings.putString(KEY_LAST_USER_ID, userId)
    }

    fun saveUser(user: AuthUser) {
        if (_cachedUser.value == user) return
        settings.putString(KEY_USER_SUB, user.sub)
        settings.putString(KEY_USER_EMAIL, user.email)
        settings.putString(KEY_USER_NAME, user.name)
        user.picture?.let { settings.putString(KEY_USER_PICTURE, it) }
            ?: settings.remove(KEY_USER_PICTURE)
        _cachedUser.value = user
    }

    private fun readCachedUser(): AuthUser? {
        val sub = settings.getStringOrNull(KEY_USER_SUB) ?: return null
        val email = settings.getStringOrNull(KEY_USER_EMAIL) ?: return null
        val name = settings.getStringOrNull(KEY_USER_NAME) ?: return null
        return AuthUser(
            sub = sub,
            email = email,
            name = name,
            picture = settings.getStringOrNull(KEY_USER_PICTURE),
        )
    }

    companion object {
        private const val KEY_TOKEN = "auth.jwt"
        private const val KEY_EXP = "auth.exp"
        private const val KEY_LAST_USER_ID = "auth.lastUserId"
        private const val KEY_USER_SUB = "auth.user.sub"
        private const val KEY_USER_EMAIL = "auth.user.email"
        private const val KEY_USER_NAME = "auth.user.name"
        private const val KEY_USER_PICTURE = "auth.user.picture"
        private val USER_KEYS = listOf(KEY_USER_SUB, KEY_USER_EMAIL, KEY_USER_NAME, KEY_USER_PICTURE)
    }
}
