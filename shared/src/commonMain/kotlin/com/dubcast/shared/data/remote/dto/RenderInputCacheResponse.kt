package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `POST /api/v2/render/inputs` 응답 — multi-variant export 시 video/audios 를 1회만 업로드하고
 * BFF 캐시 [inputId] 를 받아 후속 [/api/v2/render] 호출에서 multipart 재업로드를 생략하기 위함.
 *
 * - [inputId] : 후속 render submit 시 form 필드 `inputId` 로 다시 보냄.
 * - [expiresAt] : 캐시 만료 epoch ms. 만료 후 호출은 4xx — 상위에서 재업로드 fallback (현재는 fail).
 * - [videoSizeBytes] / [audioCount] : 디버깅·로깅용. 클라이언트가 정합성 가볍게 체크 가능.
 */
@Serializable
data class RenderInputCacheResponse(
    val inputId: String,
    val expiresAt: Long,
    val videoSizeBytes: Long,
    val audioCount: Int,
)
