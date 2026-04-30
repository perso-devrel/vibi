package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.model.SubtitleSource
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.repository.AutoSubtitleStatus
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.platform.generateId

private const val POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_ATTEMPTS = 360

class GenerateAutoSubtitlesUseCase(
    private val autoSubtitleRepository: AutoSubtitleRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val editProjectRepository: EditProjectRepository
) {
    /**
     * 1 STT + N 번역 패턴. 영상 1회 업로드, BFF 가 STT 1회 + targetLanguageCodes 각 lang 에 대해
     * Gemini 번역. 결과는 originalSrt (sourceLang) + translatedSrt × N. 모두 SubtitleClip 으로
     * 저장하면서 languageCode 로 미리보기 swap 가능.
     */
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>,
        numberOfSpeakers: Int = 1
    ): Result<Int> = runCatching {
        println("[GenerateAutoSubtitles] enter projectId=$projectId targets=$targetLanguageCodes")
        var project = editProjectRepository.getProject(projectId)
            ?: throw IllegalStateException("Project not found: $projectId")
        println("[GenerateAutoSubtitles] project loaded — submitting to BFF (1 upload + 1 STT + ${targetLanguageCodes.size} translations)")

        project = project.copy(
            autoSubtitleStatus = AutoJobStatus.RUNNING,
            autoSubtitleError = null,
            autoSubtitleJobId = null
        )
        editProjectRepository.updateProject(project)

        val jobId = autoSubtitleRepository.submit(
            sourceUri = sourceUri,
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCodes = targetLanguageCodes,
            numberOfSpeakers = numberOfSpeakers
        ).getOrElse { e ->
            println("[GenerateAutoSubtitles] submit failed: ${e.message}")
            markFailed(project, "자막 요청 실패")
            throw e
        }
        println("[GenerateAutoSubtitles] BFF accepted jobId=$jobId")

        project = project.copy(autoSubtitleJobId = jobId)
        editProjectRepository.updateProject(project)

        val ready = pollUntilReady(
            label = "subtitle",
            pollIntervalMs = POLL_INTERVAL_MS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            fetch = { autoSubtitleRepository.pollStatus(jobId).getOrThrow() }
        ) { status ->
            when (status) {
                is AutoSubtitleStatus.Ready -> PollDecision.Ready(status)
                is AutoSubtitleStatus.Failed -> PollDecision.Failed(status.reason)
                is AutoSubtitleStatus.Processing -> PollDecision.Processing
            }
        }

        // 기존 auto-자막 모두 비우고 다시 채움 — N langs 새로 받은 set 으로 교체.
        subtitleClipRepository.deleteAutoSubtitles(projectId)

        // 각 lang 별로 SRT 다운로드 + 파싱 + SubtitleClip rows 생성.
        // - sourceLanguageCode="auto" 면 Perso 가 자동 감지한 lang 을 알 수 없으므로 originalSrtUrl
        //   은 lang 라벨 못 붙여서 skip. 사용자 target 에 source 와 동일 lang 이 있으면 BFF 가 same-lang
        //   detection 으로 Gemini 호출 skip 후 origin 복사본을 translatedSrtUrlsByLang 에 넣음.
        // - sourceLanguageCode 가 명시 lang 이고 그 lang 이 translatedSrtUrlsByLang 에 이미 있으면
        //   원본/translated 가 동일 내용 → translated 만 사용 (중복 fetch 회피).
        val srtByLang: Map<String, String> = buildMap {
            ready.translatedSrtUrlsByLang.forEach { (lang, url) -> put(lang, url) }
            if (sourceLanguageCode.isNotBlank() && sourceLanguageCode != "auto" &&
                sourceLanguageCode !in ready.translatedSrtUrlsByLang
            ) {
                put(sourceLanguageCode, ready.originalSrtUrl)
            }
        }
        var totalCues = 0
        srtByLang.forEach { (lang, url) ->
            val body = autoSubtitleRepository.fetchSrt(url).getOrElse { e ->
                println("[GenerateAutoSubtitles] fetchSrt $lang failed: ${e.message}")
                return@forEach
            }
            val cues = runCatching { SrtParser.parse(body) }.getOrElse {
                println("[GenerateAutoSubtitles] parse $lang failed: ${it.message}")
                return@forEach
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
                    languageCode = lang
                )
            }
            subtitleClipRepository.addClips(rows)
            totalCues += rows.size
        }

        editProjectRepository.updateProject(
            project.copy(
                autoSubtitleStatus = AutoJobStatus.READY,
                autoSubtitleError = null
            )
        )
        totalCues
    }.onFailure { e ->
        editProjectRepository.getProject(projectId)?.let {
            markFailed(it, sanitizeMessage(e))
        }
    }

    private suspend fun markFailed(project: EditProject, message: String?) {
        editProjectRepository.updateProject(
            project.copy(
                autoSubtitleStatus = AutoJobStatus.FAILED,
                autoSubtitleError = message
            )
        )
    }

    private fun sanitizeMessage(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "자막 실패"
        else -> "자막 실패"
    }
}
