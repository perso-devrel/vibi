package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.model.SubtitleSource
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.repository.AutoSubtitleStatus
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.platform.generateId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 사용자가 수정한 한 언어 자막을 source 로 다른 언어 자막을 재생성한다.
 *
 * 흐름:
 * 1. projectId 의 sourceLang 자막 clip 들을 SRT 로 직렬화.
 * 2. BFF `/subtitles/regenerate` 호출 → jobId.
 * 3. status 폴링 → READY 도달 시 각 target lang 의 SRT URL 받음.
 * 4. 각 SRT 다운로드 → 파싱 → 해당 target lang 의 기존 자막 클립 모두 삭제 후 새 클립 추가.
 *
 * 스타일/위치는 source lang 의 자막에서 그대로 복사 (cue 시점 매칭). 매칭 안 되면 default.
 */
class RegenerateSubtitlesUseCase(
    private val autoSubtitleRepository: AutoSubtitleRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val pollIntervalMs: Long = 3_000L,
    private val maxAttempts: Int = 600, // 30 min @ 3s
) {
    suspend operator fun invoke(
        projectId: String,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>,
        onProgress: (progress: Int, reason: String?) -> Unit = { _, _ -> },
    ): Result<Unit> = runCatching {
        // sourceLang 빈 문자열 = STT 검토 흐름 ("" lang clip 들이 source). target 빈 문자열은 항상 제외.
        val targets = targetLanguageCodes.filter { it.isNotBlank() && it != sourceLanguageCode }.distinct()
        require(targets.isNotEmpty()) { "at least one different target language required" }

        // 1) source lang clips → SRT. observeClips.first() 로 현재 스냅샷만 취함.
        val allClips = subtitleClipRepository.observeClips(projectId).first()
        val sourceClips = allClips.filter { it.languageCode == sourceLanguageCode }
        require(sourceClips.isNotEmpty()) { "no subtitle clips for source language $sourceLanguageCode" }
        val srtBody = SrtSerializer.fromClips(sourceClips)
        val srtBytes = srtBody.encodeToByteArray()

        // 2) submit.
        onProgress(0, "재번역 요청 중")
        val jobId = autoSubtitleRepository.regenerate(srtBytes, sourceLanguageCode, targets).getOrThrow()

        // 3) poll until READY.
        var ready: AutoSubtitleStatus.Ready? = null
        var attempt = 0
        while (attempt < maxAttempts) {
            val status = autoSubtitleRepository.pollStatus(jobId).getOrThrow()
            when (status) {
                is AutoSubtitleStatus.Ready -> { ready = status; break }
                is AutoSubtitleStatus.Failed -> error("재번역 실패: ${status.reason ?: "unknown"}")
                is AutoSubtitleStatus.Processing -> onProgress(status.progress, status.progressReason)
            }
            delay(pollIntervalMs)
            attempt++
        }
        val readyResult = ready ?: error("재번역 타임아웃")

        // 4) 각 target SRT 다운로드 → SubtitleClip 으로 import (덮어쓰기).
        // 매칭 정책: source clips 의 (startMs, endMs) 와 가장 가까운 cue 의 스타일/위치를 상속.
        val sourceByTime = sourceClips.associateBy { it.startMs to it.endMs }
        for (lang in targets) {
            val url = readyResult.translatedSrtUrlsByLang[lang] ?: continue
            val srt = autoSubtitleRepository.fetchSrt(url).getOrThrow()
            val cues = SrtParser.parse(srt)
            // 기존 lang clip 전부 제거.
            val existing = allClips.filter { it.languageCode == lang }
            existing.forEach { subtitleClipRepository.deleteClip(it.id) }
            val newClips = cues.map { cue ->
                val base = sourceByTime[cue.startMs to cue.endMs]
                SubtitleClip(
                    id = generateId(),
                    projectId = projectId,
                    text = cue.text,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    position = base?.position ?: SubtitlePosition(),
                    source = SubtitleSource.AUTO,
                    languageCode = lang,
                    fontFamily = base?.fontFamily ?: SubtitleClip.DEFAULT_FONT_FAMILY,
                    fontSizeSp = base?.fontSizeSp ?: SubtitleClip.DEFAULT_FONT_SIZE_SP,
                    colorHex = base?.colorHex ?: SubtitleClip.DEFAULT_COLOR_HEX,
                    backgroundColorHex = base?.backgroundColorHex ?: SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX,
                )
            }
            subtitleClipRepository.addClips(newClips)
        }
        onProgress(100, "완료")
    }
}
