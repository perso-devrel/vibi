package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** BFF [ChatRequest] 미러 — 모바일 → /api/v2/chat. */
@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val projectContext: ProjectContextDto,
    val locale: String = "ko",
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
    // 클라이언트 내부 식별자 — "처리 중" 메시지를 결과로 in-place 갱신용. @Transient 라 BFF
    // 송신 JSON 에 절대 포함되지 않음 (encodeDefaults=true 환경에서 안전).
    @Transient
    val id: String? = null,
)

@Serializable
data class ProjectContextDto(
    val segments: List<ContextSegmentDto> = emptyList(),
    val subtitleClips: List<ContextSubtitleClipDto> = emptyList(),
    val dubClips: List<ContextDubClipDto> = emptyList(),
    val bgmClips: List<ContextBgmClipDto> = emptyList(),
    val separationStems: List<ContextStemDto> = emptyList(),
    val separationDirectives: List<ContextSeparationDirectiveDto> = emptyList(),
    val processingSeparations: List<ContextProcessingSeparationDto> = emptyList(),
    val currentPlayheadMs: Long = 0L,
    val selectedSegmentId: String? = null,
    val selectedClipId: String? = null,
    val isRangeSelecting: Boolean = false,
    val pendingRangeStartMs: Long? = null,
    val pendingRangeEndMs: Long? = null,
    val videoDurationMs: Long = 0L,
)

@Serializable
data class ContextSegmentDto(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val sourceUri: String,
    val speedScale: Float = 1.0f,
    val volumeScale: Float = 1.0f,
)

@Serializable
data class ContextSubtitleClipDto(
    val id: String,
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val languageCode: String,
)

@Serializable
data class ContextDubClipDto(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val voiceId: String,
)

@Serializable
data class ContextBgmClipDto(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
)

@Serializable
data class ContextStemDto(
    val stemId: String,
    val label: String,
    val volume: Float,
    val selected: Boolean,
)

/**
 * 이미 음성분리된 구간 — Gemini 가 (1) 중복 분리 회피 (2) 비용 안내 (3) 대안 제시
 * (기존 삭제 후 재분리 vs 짧은 분할) 판단을 위해 참조. 1분당 비용 기준.
 */
@Serializable
data class ContextSeparationDirectiveDto(
    val id: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val durationMs: Long,
    val numberOfSpeakers: Int,
)

/**
 * 진행 중인 음원분리 잡 — 아직 directive 로 commit 되지 않음. Gemini 가
 * (1) "지금 분리 중인 거 끝나면" 발화 매칭 (2) WF-4 중복 분리 회피 (3) WF-7 BGM 정렬 candidate.
 * presence-only — range 외 metadata 미노출.
 */
@Serializable
data class ContextProcessingSeparationDto(
    val rangeStartMs: Long,
    val rangeEndMs: Long,
)

@Serializable
data class ChatResponseDto(
    val kind: String,
    val text: String? = null,
    val proposal: ProposalDto? = null,
)

@Serializable
data class ProposalDto(
    val rationale: String,
    val steps: List<ToolCallDto>,
)

@Serializable
data class ToolCallDto(
    val name: String,
    val args: JsonObject,
)
