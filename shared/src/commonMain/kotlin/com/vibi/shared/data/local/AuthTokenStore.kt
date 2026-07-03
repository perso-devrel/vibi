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

    // BFF 발급 직후의 토큰을 프로세스 메모리에 보관 — Keychain 쓰기/읽기가 실패하거나(iOS 잠금·keychain
    // 특이동작·미서명 빌드) 지연돼도 현재 세션의 모든 요청이 방금 받은 토큰을 헤더에 싣게 한다. durable
    // 영속은 [secureSettings] 가 담당하고 이 캐시는 그 위의 안전망 — 프로세스 종료 시 사라지며 재시작
    // 시 secureSettings 에서 복원된다. (로그인 200 직후 401 missing_token 회귀의 정공법 방어.)
    private var memToken: String? = null
    private var memExpiresAt: Long = 0L

    fun saveToken(jwt: String, expiresAt: Long) {
        // 인메모리 먼저 — 동기적으로, Keychain 결과와 무관하게 현재 세션에서 즉시 읽히도록.
        memToken = jwt
        memExpiresAt = expiresAt
        // Keychain 접근이 실패해도(미서명 빌드·entitlement 부재·기기 잠금 등) 앱이 죽지 않도록 보호.
        // 실패 시 durable 영속만 누락(인메모리 캐시로 현재 세션은 유지) → 다음 실행에 재로그인.
        // remove(→SecItemDelete) 후 put(→SecItemAdd) — 기존 항목을 지우고 새로 써 항상 최신값 보장.
        runCatching {
            secureSettings.remove(KEY_TOKEN)
            secureSettings.remove(KEY_EXP)
            secureSettings.putString(KEY_TOKEN, jwt)
            secureSettings.putLong(KEY_EXP, expiresAt)
        }
    }

    /**
     * 만료된 토큰은 자동 폐기 후 null. 인메모리 캐시 우선 → durable([secureSettings]) 폴백.
     * Keychain 접근 실패 시에도 null(→ 로그인)로 안전 degrade.
     */
    fun getValidToken(): String? {
        val now = currentTimeMillis()
        // 1) 인메모리 우선 — 방금 로그인/이전 읽기로 확보한 토큰. Keychain 히컵·잠금과 무관.
        memToken?.let { if (memExpiresAt > now) return it }
        // 2) durable 폴백 — 프로세스 재시작 후 첫 접근 등. 읽히면 메모리로 승격, 만료면 폐기.
        return runCatching {
            val exp = secureSettings.getLongOrNull(KEY_EXP) ?: return@runCatching null
            if (exp <= now) {
                clear()
                return@runCatching null
            }
            secureSettings.getStringOrNull(KEY_TOKEN)?.also {
                memToken = it
                memExpiresAt = exp
            }
        }.getOrNull()
    }

    fun clear() {
        memToken = null
        memExpiresAt = 0L
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
