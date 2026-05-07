package com.dubcast.shared.data.remote.dto

import com.dubcast.shared.domain.model.AuthUser
import kotlinx.serialization.Serializable

@Serializable
data class GoogleAuthRequestDto(
    val idToken: String,
)

@Serializable
data class AuthUserDto(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String? = null,
) {
    fun toDomain(): AuthUser = AuthUser(sub = sub, email = email, name = name, picture = picture)
}

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val expiresAt: Long,
    val user: AuthUserDto,
)
