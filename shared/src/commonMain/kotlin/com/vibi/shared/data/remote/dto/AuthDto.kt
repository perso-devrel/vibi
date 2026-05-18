package com.vibi.shared.data.remote.dto

import com.vibi.shared.domain.model.AuthUser
import kotlinx.serialization.Serializable

@Serializable
data class GoogleAuthRequestDto(
    val idToken: String,
)

/**
 * Apple Sign In ID Token 교환 요청.
 *
 * - [fullName] — Apple 은 사용자 동의 흐름에서 **최초 1회만** fullName 을 준다.
 *   iOS 가 그 시점에 받은 fullName 을 그대로 전달하고, 두 번째 로그인부터는 null.
 *   서버는 신규 가입 시에만 이 값을 user.name 으로 채우고, 이후엔 DB 의 기존 name 보존.
 */
@Serializable
data class AppleAuthRequestDto(
    val idToken: String,
    val fullName: String? = null,
)

@Serializable
data class AuthUserDto(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String? = null,
    val role: String = AuthUser.ROLE_USER,
) {
    fun toDomain(): AuthUser = AuthUser(
        sub = sub,
        email = email,
        name = name,
        picture = picture,
        role = role,
    )
}

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val expiresAt: Long,
    val user: AuthUserDto,
)
