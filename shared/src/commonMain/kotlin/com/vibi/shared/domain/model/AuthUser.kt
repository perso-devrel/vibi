package com.vibi.shared.domain.model

/**
 * 로그인된 사용자 식별 정보. v1 은 BFF 가 영속화하지 않고 JWT claim 으로만 보존하므로
 * 클라이언트도 별도 캐시 없이 로그인 응답에서 받아 in-memory 로 들고 다닌다.
 */
data class AuthUser(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String? = null,
    /** BFF JWT 의 `role` claim. 'user' 또는 'admin'. */
    val role: String = ROLE_USER,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
    }
}
