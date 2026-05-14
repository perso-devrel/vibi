package com.vibi.shared.domain.chat

import com.vibi.shared.data.remote.dto.ToolCallDto
import com.vibi.shared.domain.model.SeparationMediaType
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.StemKind
import com.vibi.shared.ui.timeline.TimelineViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 채팅 proposal 의 step 들을 [TimelineViewModel.onXxx] 메서드 1회 호출로 dispatch. 새 비즈니스 로직 0.
 *
 * 등록되지 않은 name 은 [DispatchResult.Failure] — BFF 1차 방어선이 막아야 하지만 디스패처도 거부.
 * 한 step 이라도 실패하면 즉시 중단 → 부분 적용 상태가 사용자에게 가시화돼야 한다.
 */
class ChatToolDispatcher {

    suspend fun dispatch(steps: List<ToolCallDto>, vm: TimelineViewModel): DispatchResult {
        val applied = mutableListOf<String>()
        for ((idx, step) in steps.withIndex()) {
            try {
                executeStep(step, vm)
                applied += labelFor(step)
            } catch (e: Exception) {
                return DispatchResult.Failure(
                    appliedLabels = applied,
                    failedAtIndex = idx,
                    failedLabel = labelFor(step),
                    message = e.message ?: "알 수 없는 오류",
                )
            }
        }
        return DispatchResult.Success(applied)
    }

    private suspend fun executeStep(step: ToolCallDto, vm: TimelineViewModel) {
        val a = step.args
        when (step.name) {
            // --- 영상 편집 ---
            // applyXxxFromChat 은 UI 슬라이더 clamp 를 우회하는 code-path. dispatcher 가 받은
            // startMs/endMs 가 그대로 use case 로 흘러간다 — range 모드 진입 불필요.
            "delete_segment_range" -> {
                vm.applyDeleteRangeFromChat(a.requireLong("startMs"), a.requireLong("endMs"))
            }
            "duplicate_segment_range" -> {
                vm.applyDuplicateRangeFromChat(a.requireLong("startMs"), a.requireLong("endMs"))
            }
            "update_segment_volume" -> {
                val total = vm.uiState.value.videoDurationMs
                vm.applyVolumeRangeFromChat(
                    start = a.optLong("startMs") ?: 0L,
                    end = a.optLong("endMs") ?: total,
                    value = a.requireFloat("volumeScale"),
                )
            }
            "update_segment_speed" -> {
                val total = vm.uiState.value.videoDurationMs
                vm.applySpeedRangeFromChat(
                    start = a.optLong("startMs") ?: 0L,
                    end = a.optLong("endMs") ?: total,
                    value = a.requireFloat("speedScale"),
                )
            }
            // --- 음성 분리 ---
            // tool def 가 startMs/endMs 로 통일됐지만 chat history 의 옛 호출이 trimStartMs/trimEndMs
            // 로 남아있을 수 있어 둘 다 받아 새 이름 우선. numberOfSpeakers 는 Perso audio-separation
            // 전용 endpoint 가 받지 않는 dead 인자라 무시.
            "separate_audio_range" -> {
                val bgmId = a.optString("bgmClipId")
                val segId = a.optString("segmentId")
                if (bgmId == null && segId == null) {
                    throw IllegalArgumentException("segmentId or bgmClipId required")
                }
                if (bgmId != null && segId != null) {
                    throw IllegalArgumentException("segmentId 와 bgmClipId 는 동시에 줄 수 없습니다 (XOR)")
                }
                val startMs = a.optLong("startMs") ?: a.optLong("trimStartMs")
                val endMs = a.optLong("endMs") ?: a.optLong("trimEndMs")
                vm.applySeparateRangeFromChat(
                    segmentId = segId,
                    bgmClipId = bgmId,
                    trimStartMs = startMs,
                    trimEndMs = endMs,
                )
            }
            "update_stem_volume" -> {
                vm.applyUpdateStemVolumeFromChat(a.requireString("stemId"), a.requireFloat("volume"))
            }
            // --- 자막·더빙 (영상) ---
            // applyGenerateXxxFromChat 은 onSetLocalizationMode/onToggleLocalizationLang/
            // onStartLocalization UI flow 우회. 기존 경로는 silent return 분기가 많아
            // (source 없음 / filter 결과 빈 리스트 / review-mode 진입) dispatcher 가 success
            // 반환했지만 BFF 호출이 안 일어나는 사고 발생.
            "update_subtitle_text" -> {
                vm.onUpdateSubtitleText(a.requireString("clipId"), a.requireString("text"))
            }
            "transcribe_for_subtitles" -> {
                vm.applyTranscribeForSubtitlesFromChat(a.requireStringArray("targetLanguageCodes"))
            }
            "apply_subtitles_with_script" -> {
                vm.applyApplySubtitlesWithScriptFromChat(
                    srt = a.optString("srt"),
                    targetLanguageCodes = a.requireStringArray("targetLanguageCodes"),
                )
            }
            "generate_subtitles" -> {
                vm.applyGenerateSubtitlesFromChat(a.requireStringArray("targetLanguageCodes"))
            }
            "generate_dub" -> {
                vm.applyGenerateDubFromChat(a.requireString("targetLanguageCode"))
            }
            // --- BGM 위치/볼륨 ---
            "move_bgm_clip" -> {
                vm.onUpdateBgmStartMs(a.requireString("clipId"), a.requireLong("newStartMs"))
            }
            "update_bgm_volume" -> {
                vm.onUpdateBgmVolume(a.requireString("clipId"), a.requireFloat("volumeScale"))
            }
            "update_bgm_range" -> {
                vm.applyUpdateBgmRangeFromChat(
                    clipId = a.requireString("clipId"),
                    newStartMs = a.requireLong("newStartMs"),
                    newEndMs = a.requireLong("newEndMs"),
                )
            }
            // --- BGM 자막·더빙 (Stage -1 prereq 의존) ---
            "generate_subtitles_for_bgm" -> {
                vm.onGenerateAutoSubtitlesForBgmClip(
                    clipId = a.requireString("clipId"),
                    targetLanguageCodes = a.requireStringArray("targetLanguageCodes"),
                )
            }
            "generate_dub_for_bgm" -> {
                vm.onGenerateAutoDubForBgmClip(
                    clipId = a.requireString("clipId"),
                    targetLanguageCode = a.requireString("targetLanguageCode"),
                )
            }
            else -> throw IllegalArgumentException("Unknown tool: ${step.name}")
        }
    }

    /** 사용자 친화 step 라벨 — ProposalCard 에 그대로 표시. */
    fun labelFor(step: ToolCallDto): String {
        val a = step.args
        return when (step.name) {
            "delete_segment_range" -> "구간 삭제 (${a.optLong("startMs") ?: 0}–${a.optLong("endMs") ?: 0}ms)"
            "duplicate_segment_range" -> "구간 복제 (${a.optLong("startMs") ?: 0}–${a.optLong("endMs") ?: 0}ms)"
            "update_segment_volume" -> "세그먼트 볼륨 ${(a.optFloat("volumeScale") ?: 1f).times(100).toInt()}%"
            "update_segment_speed" -> "세그먼트 속도 ${(a.optFloat("speedScale") ?: 1f).times(100).toInt()}%"
            "separate_audio_range" -> {
                val s = a.optLong("startMs") ?: a.optLong("trimStartMs")
                val e = a.optLong("endMs") ?: a.optLong("trimEndMs")
                if (s != null && e != null) "음원 분리 (${s}–${e}ms)" else "음원 분리 (전체)"
            }
            "update_stem_volume" -> {
                val stemId = a.optString("stemId") ?: ""
                val pct = (a.optFloat("volume") ?: 1f).times(100).toInt()
                val name = stemDisplayName(stemId)
                when {
                    pct == 0 -> "$name 음소거"
                    pct == 100 -> "$name 볼륨 원래대로"
                    else -> "$name 볼륨 ${pct}%"
                }
            }
            "update_subtitle_text" -> "자막 텍스트 변경"
            "transcribe_for_subtitles" -> "스크립트 생성 (검토용): ${a.optStringArray("targetLanguageCodes")?.joinToString(",")}"
            "apply_subtitles_with_script" -> {
                val edited = if (a.optString("srt").isNullOrBlank()) "" else " (수정 반영)"
                "자막 생성: ${a.optStringArray("targetLanguageCodes")?.joinToString(",")}$edited"
            }
            "generate_subtitles" -> "자막 생성 (검토 생략): ${a.optStringArray("targetLanguageCodes")?.joinToString(",")}"
            "generate_dub" -> "더빙 생성: ${a.optString("targetLanguageCode")}"
            "move_bgm_clip" -> "음원 위치 이동"
            "update_bgm_volume" -> "음원 볼륨 ${(a.optFloat("volumeScale") ?: 1f).times(100).toInt()}%"
            "update_bgm_range" -> "음원 구간 정렬 (${a.optLong("newStartMs") ?: 0}–${a.optLong("newEndMs") ?: 0}ms)"
            "generate_subtitles_for_bgm" -> "음원 자막 생성"
            "generate_dub_for_bgm" -> "음원 더빙 생성"
            else -> step.name
        }
    }

    /**
     * stemId → 사용자 친화 표기. "stem" 같은 jargon 대신 "배경음/보컬/N번 화자" 로.
     * Stem.kindFromId 가 BACKGROUND/VOICE_ALL/SPEAKER/UNKNOWN 분류.
     */
    private fun stemDisplayName(stemId: String): String = when (Stem.kindFromId(stemId)) {
        StemKind.BACKGROUND -> "배경음"
        StemKind.VOICE_ALL -> "보컬"
        StemKind.SPEAKER -> {
            val idx = Stem.speakerIndexFromId(stemId)
            if (idx != null) "${idx}번 화자" else "화자"
        }
        StemKind.UNKNOWN -> stemId
    }

    // ── JsonObject helpers ─────────────────────────────────────────────────────
    private fun JsonObject.requireString(key: String): String =
        (get(key) as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Missing string arg: $key")

    private fun JsonObject.requireLong(key: String): Long =
        (get(key) as? JsonPrimitive)?.long
            ?: throw IllegalArgumentException("Missing long arg: $key")

    private fun JsonObject.requireFloat(key: String): Float =
        (get(key) as? JsonPrimitive)?.float
            ?: throw IllegalArgumentException("Missing float arg: $key")

    private fun JsonObject.requireInt(key: String): Int =
        (get(key) as? JsonPrimitive)?.long?.toInt()
            ?: throw IllegalArgumentException("Missing int arg: $key")

    private fun JsonObject.requireStringArray(key: String): List<String> =
        (get(key) as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }
            ?: throw IllegalArgumentException("Missing string array arg: $key")

    private fun JsonObject.optString(key: String): String? =
        (get(key) as? JsonPrimitive)?.content

    private fun JsonObject.optLong(key: String): Long? =
        (get(key) as? JsonPrimitive)?.long

    private fun JsonObject.optFloat(key: String): Float? =
        (get(key) as? JsonPrimitive)?.float

    private fun JsonObject.optInt(key: String): Int? =
        (get(key) as? JsonPrimitive)?.long?.toInt()

    private fun JsonObject.optStringArray(key: String): List<String>? =
        (get(key) as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }
}

sealed interface DispatchResult {
    data class Success(val appliedLabels: List<String>) : DispatchResult
    data class Failure(
        val appliedLabels: List<String>,
        val failedAtIndex: Int,
        val failedLabel: String,
        val message: String,
    ) : DispatchResult
}
