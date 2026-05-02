package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable
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
)

@Serializable
data class ProjectContextDto(
    val segments: List<ContextSegmentDto> = emptyList(),
    val subtitleClips: List<ContextSubtitleClipDto> = emptyList(),
    val dubClips: List<ContextDubClipDto> = emptyList(),
    val bgmClips: List<ContextBgmClipDto> = emptyList(),
    val separationStems: List<ContextStemDto> = emptyList(),
    val currentPlayheadMs: Long = 0L,
    val selectedSegmentId: String? = null,
    val selectedClipId: String? = null,
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
