package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.model.SubtitleSource
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.repository.AutoSubtitleStatus
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.platform.generateId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 자막/더빙 생성 전 STT 단계 단독 실행. 영상 → BFF (Perso STT) → originalSrt → SubtitleClip(lang="")
 * 으로 저장. 사용자가 검토·수정 후 [RegenerateSubtitlesUseCase] 로 다국어 자막 일괄 생성.
 *
 * sourceLanguageCode = "auto" 고정 (Perso STT 자동 감지). 저장 시 lang="" 로 마킹 — 원본/언어 미지정 의미.
 */
class GenerateOriginalScriptUseCase(
    private val autoSubtitleRepository: AutoSubtitleRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val editProjectRepository: EditProjectRepository,
    private val pollIntervalMs: Long = 3_000L,
    private val maxAttempts: Int = 600,
) {
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        mediaType: String = "VIDEO",
    ): Result<Int> = runCatching {
        val project = editProjectRepository.getProject(projectId)
            ?: error("프로젝트를 찾을 수 없습니다: $projectId")

        editProjectRepository.updateProject(
            project.copy(
                autoSubtitleStatus = AutoJobStatus.RUNNING,
                autoSubtitleError = null,
                autoSubtitleJobId = null,
            )
        )

        val jobId = autoSubtitleRepository.submit(
            sourceUri = sourceUri,
            mediaType = mediaType,
            sourceLanguageCode = "auto",
            targetLanguageCodes = emptyList(),
            numberOfSpeakers = 1,
        ).getOrThrow()

        editProjectRepository.updateProject(
            (editProjectRepository.getProject(projectId) ?: project).copy(autoSubtitleJobId = jobId)
        )

        val ready = pollUntilReady(jobId)
        val srt = autoSubtitleRepository.fetchSrt(ready.originalSrtUrl).getOrThrow()
        val cues = SrtParser.parse(srt)

        // 기존 lang="" 자막 클립 모두 정리 후 새로 추가.
        val existing = subtitleClipRepository.observeClips(projectId).first()
        existing.filter { it.languageCode.isBlank() }.forEach {
            subtitleClipRepository.deleteClip(it.id)
        }
        val rows = cues.map { cue ->
            SubtitleClip(
                id = generateId(),
                projectId = projectId,
                text = cue.text,
                startMs = cue.startMs,
                endMs = cue.endMs,
                position = SubtitlePosition(),
                source = SubtitleSource.AUTO,
                languageCode = "",
            )
        }
        subtitleClipRepository.addClips(rows)

        editProjectRepository.updateProject(
            (editProjectRepository.getProject(projectId) ?: project).copy(
                autoSubtitleStatus = AutoJobStatus.READY,
                autoSubtitleError = null,
            )
        )
        rows.size
    }.onFailure { err ->
        runCatching {
            val p = editProjectRepository.getProject(projectId) ?: return@runCatching
            editProjectRepository.updateProject(
                p.copy(
                    autoSubtitleStatus = AutoJobStatus.FAILED,
                    autoSubtitleError = err.message,
                )
            )
        }
    }

    private suspend fun pollUntilReady(jobId: String): AutoSubtitleStatus.Ready {
        var attempt = 0
        while (attempt < maxAttempts) {
            val status = autoSubtitleRepository.pollStatus(jobId).getOrThrow()
            when (status) {
                is AutoSubtitleStatus.Ready -> return status
                is AutoSubtitleStatus.Failed -> error(status.reason ?: "STT 실패")
                is AutoSubtitleStatus.Processing -> Unit
            }
            delay(pollIntervalMs)
            attempt++
        }
        error("STT 폴링 타임아웃")
    }
}
