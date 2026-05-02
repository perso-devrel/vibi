package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.dto.ChatRequestDto
import com.dubcast.shared.data.remote.dto.ChatResponseDto

/**
 * BffApi.chat 의 얇은 래퍼 — 다른 Repository 와 같은 패턴 (Result<T> 변환 + 에러 격리).
 * v1 은 비즈니스 로직 0 — 채팅은 BFF/Gemini 가 담당, 모바일은 라우팅·UI 만.
 */
class ChatRepository(
    private val bffApi: BffApi,
) {
    suspend fun chat(request: ChatRequestDto): Result<ChatResponseDto> = runCatching {
        bffApi.chat(request)
    }
}
