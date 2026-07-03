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
 * 토큰/만료([KEY_TOKEN]/[KEY_EXP])는 보안 저장소 [secureSettings] 에 둔다 — iOS 는 Keychain,
 * Android 는 prefs(+ manifest allowBackup=false 로 백업 유출 차단). 비민감한 프로필 캐시
 * (name/email/picture)와 lastUserId 는 일반 [settings]. 프로필 캐시는 메뉴 프로필을 BFF
 * round-trip 없이 즉시 보여주기 위한 것. (기존 평문 저장 토큰은 업데이트 후 재로그인으로 정리.)
 */
class AuthTokenStore(
    private val settings: Settings,
    private val secureSettings: Settings,
) {

    private val _cachedUser = MutableStateFlow<AuthUser?>(readCachedUser())
    /** 마지막 로그인 응답의 user. 로그아웃 시 null. */
    val cachedUser: StateFlow<AuthUser?> = _cachedUser.asStateFlow()

    fun saveToken(jwt: String, expiresAt: Long) {
        // Keychain 접근이 실패해도(미서명 빌드·entitlement 부재·기기 잠금 등) 앱이 죽지 않도록 보호.
        // 실패 시 토큰 미영속 → 다음 실행에 재로그인. 서명된 출시 빌드에선 정상 영속.
        runCatching {
            // 기존 항목이 있으면 putString 은 SecItemUpdate(값만 교체)로 떨어져, KeychainSettings 에
            // 지정한 accessibility(AfterFirstUnlockThisDeviceOnly)가 재적용되지 않는다(accessibility 는
            // SecItemAdd 시점에만 적용). 항상 SecItemAdd 경로를 타도록 remove(→SecItemDelete) 후
            // put(→SecItemAdd) — 매 저장이 올바른 accessibility 로 기록되게 한다.
            secureSettings.remove(KEY_TOKEN)
            secureSettings.remove(KEY_EXP)
            secureSettings.putString(KEY_TOKEN, jwt)
            secureSettings.putLong(KEY_EXP, expiresAt)
        }
    }

    /**
     * 구버전(accessibility 미지정 = 기본 WhenUnlocked)으로 저장된 토큰을 현재 accessibility
     * (AfterFirstUnlockThisDeviceOnly)로 1회 이관. SecItemUpdate 는 accessibility 를 바꾸지 않으므로
     * remove+put(=delete+add)로 강제 재생성한다. 앱 시작(포그라운드=잠금 해제) 시 [restoreSession]
     * 에서 호출 — 그 시점엔 Keychain 읽기가 되므로 정상 이관. [settings] 플래그로 멱등(1회만).
     * 잠금/접근 실패로 읽지 못하면 flag 를 세우지 않아 다음 부팅에 재시도한다.
     */
    fun migrateSecureAccessibilityOnce() {
        if (settings.getBoolean(KEY_ACCESSIBILITY_MIGRATED, false)) return
        runCatching {
            val token = secureSettings.getStringOrNull(KEY_TOKEN)
            val exp = secureSettings.getLongOrNull(KEY_EXP)
            if (token != null && exp != null) {
                secureSettings.remove(KEY_TOKEN)
                secureSettings.remove(KEY_EXP)
                secureSettings.putString(KEY_TOKEN, token)
                secureSettings.putLong(KEY_EXP, exp)
            }
            // 이 지점 도달 = 읽기/재기록 성공(또는 토큰 부재). 부재 시 다음 로그인의 saveToken 이
            // 올바른 accessibility 로 쓰므로 flag 를 세워도 안전.
            settings.putBoolean(KEY_ACCESSIBILITY_MIGRATED, true)
        }
    }

    /** 만료된 토큰은 자동 폐기 후 null. Keychain 접근 실패 시에도 null(→ 로그인)로 안전 degrade. */
    fun getValidToken(): String? = runCatching {
        val exp = secureSettings.getLongOrNull(KEY_EXP) ?: return@runCatching null
        if (exp <= currentTimeMillis()) {
            clear()
            return@runCatching null
        }
        secureSettings.getStringOrNull(KEY_TOKEN)
    }.getOrNull()

    fun clear() {
        runCatching {
            secureSettings.remove(KEY_TOKEN)
            secureSettings.remove(KEY_EXP)
        }
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
        settings.putString(KEY_USER_ROLE, user.role)
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
            role = settings.getStringOrNull(KEY_USER_ROLE) ?: AuthUser.ROLE_USER,
        )
    }

    companion object {
        private const val KEY_TOKEN = "auth.jwt"
        private const val KEY_EXP = "auth.exp"
        // 비민감 플래그(일반 settings). Keychain accessibility 이관 1회 수행 여부.
        private const val KEY_ACCESSIBILITY_MIGRATED = "auth.accessibilityMigrated"
        private const val KEY_LAST_USER_ID = "auth.lastUserId"
        private const val KEY_USER_SUB = "auth.user.sub"
        private const val KEY_USER_EMAIL = "auth.user.email"
        private const val KEY_USER_NAME = "auth.user.name"
        private const val KEY_USER_PICTURE = "auth.user.picture"
        private const val KEY_USER_ROLE = "auth.user.role"
        private val USER_KEYS = listOf(
            KEY_USER_SUB, KEY_USER_EMAIL, KEY_USER_NAME, KEY_USER_PICTURE, KEY_USER_ROLE,
        )
    }
}
