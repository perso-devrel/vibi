package com.dubcast.shared.domain.chat

import com.dubcast.shared.data.remote.dto.ToolCallDto
import com.dubcast.shared.domain.model.SeparationMediaType
import com.dubcast.shared.ui.timeline.TimelineViewModel
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
            "delete_segment_range" -> {
                vm.onSetPendingRangeStart(a.requireLong("startMs"))
                vm.onSetPendingRangeEnd(a.requireLong("endMs"))
                vm.onDeleteRange()
            }
            "duplicate_segment_range" -> {
                vm.onSetPendingRangeStart(a.requireLong("startMs"))
                vm.onSetPendingRangeEnd(a.requireLong("endMs"))
                vm.onDuplicateRange()
            }
            "update_segment_volume" -> {
                vm.onSetPendingRangeStart(0L)
                vm.onSetPendingRangeEnd(Long.MAX_VALUE)
                vm.onApplyRangeVolume(a.requireFloat("volumeScale"))
            }
            "update_segment_speed" -> {
                vm.onSetPendingRangeStart(0L)
                vm.onSetPendingRangeEnd(Long.MAX_VALUE)
                vm.onApplyRangeSpeed(a.requireFloat("speedScale"))
            }
            // --- 음성 분리 ---
            "separate_audio_range" -> {
                val bgmId = a.optString("bgmClipId")
                if (bgmId != null) {
                    vm.onStartBgmSeparation(bgmId)
                } else {
                    val segId = a.optString("segmentId")
                        ?: throw IllegalArgumentException("segmentId or bgmClipId required")
                    val ts = a.optLong("trimStartMs")
                    val te = a.optLong("trimEndMs")
                    if (ts != null && te != null) {
                        vm.onSetPendingRangeStart(ts)
                        vm.onSetPendingRangeEnd(te)
                    }
                    vm.onShowAudioSeparationSheet(segId)
                    vm.onUpdateSeparationSpeakers(a.optInt("numberOfSpeakers") ?: 2)
                    vm.onStartSeparation()
                }
            }
            "update_stem_volume" -> {
                vm.onUpdateStemVolume(a.requireString("stemId"), a.requireFloat("volume"))
            }
            // --- 자막·더빙 (영상) ---
            "update_subtitle_text" -> {
                vm.onUpdateSubtitleText(a.requireString("clipId"), a.requireString("text"))
            }
            "generate_subtitles" -> {
                val targets = a.requireStringArray("targetLanguageCodes")
                val mode = "subtitle"
                vm.onSetLocalizationMode(mode)
                targets.forEach { vm.onToggleLocalizationLang(it) }
                vm.onStartLocalization()
            }
            "generate_dub" -> {
                vm.onSetLocalizationMode("dub")
                vm.onToggleLocalizationLang(a.requireString("targetLanguageCode"))
                vm.onStartLocalization()
            }
            // --- BGM 위치/볼륨 ---
            "move_bgm_clip" -> {
                vm.onUpdateBgmStartMs(a.requireString("clipId"), a.requireLong("newStartMs"))
            }
            "update_bgm_volume" -> {
                vm.onUpdateBgmVolume(a.requireString("clipId"), a.requireFloat("volumeScale"))
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
            "separate_audio_range" -> "음원 분리 (화자 ${a.optInt("numberOfSpeakers") ?: 2})"
            "update_stem_volume" -> "stem 볼륨 ${(a.optFloat("volume") ?: 1f).times(100).toInt()}%"
            "update_subtitle_text" -> "자막 텍스트 변경"
            "generate_subtitles" -> "자막 생성: ${a.optStringArray("targetLanguageCodes")?.joinToString(",")}"
            "generate_dub" -> "더빙 생성: ${a.optString("targetLanguageCode")}"
            "move_bgm_clip" -> "음원 위치 이동"
            "update_bgm_volume" -> "음원 볼륨 ${(a.optFloat("volumeScale") ?: 1f).times(100).toInt()}%"
            "generate_subtitles_for_bgm" -> "음원 자막 생성"
            "generate_dub_for_bgm" -> "음원 더빙 생성"
            else -> step.name
        }
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
