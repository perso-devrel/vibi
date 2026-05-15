package com.vibi.shared.domain.usecase.subtitle

import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.SubtitleClip
import com.vibi.shared.domain.model.SubtitlePosition
import com.vibi.shared.domain.model.SubtitleSource
import com.vibi.shared.domain.repository.AutoSubtitleRepository
import com.vibi.shared.domain.repository.AutoSubtitleStatus
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SubtitleClipRepository
import com.vibi.shared.domain.usecase.render.EnsureLatestRenderUseCase
import com.vibi.shared.domain.usecase.render.RenderKind
import com.vibi.shared.platform.generateId

private const val POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_ATTEMPTS = 360

class GenerateAutoSubtitlesUseCase(
    private val autoSubtitleRepository: AutoSubtitleRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val editProjectRepository: EditProjectRepository,
    private val ensureLatestRender: EnsureLatestRenderUseCase,
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
        numberOfSpeakers: Int = 1,
        onRenderProgress: (percent: Int) -> Unit = {},
        includeOriginalLanguage: Boolean = false,
    ): Result<Int> = runCatching {
        var project = editProjectRepository.getProject(projectId)
            ?: throw IllegalStateException("Project not found: $projectId")

        project = project.copy(
            autoSubtitleStatus = AutoJobStatus.RUNNING,
            autoSubtitleError = null,
            autoSubtitleJobId = null
        )
        editProjectRepository.updateProject(project)

        // 편집 영상이 필요한 경우 audio-only render 잡 ID 보장 (자막은 audio 만 필요 — 5–10x 빠름).
        // null = 무편집 → 원본 sourceUri 사용.
        val editedRenderJobId = ensureLatestRender(
            projectId = projectId,
            kind = RenderKind.AUDIO,
            onProgress = onRenderProgress,
        ).getOrElse { e ->
            markFailed(project, "편집 영상 준비 실패")
            throw e
        }
        // ensureLatestRender 가 project 를 갱신했을 수 있으므로 다시 읽음.
        project = editProjectRepository.getProject(projectId) ?: project

        // audio-only render 결과를 source 로 쓸 때는 mediaType 을 "AUDIO" 로 강제. BFF 의 자막 서비스가
        // mediaType 기준으로 `-vn` 추출을 분기하므로 일치 필수. editedRenderJobId 가 null 이면 원본
        // sourceUri 의 mediaType (caller 가 전달한 값) 그대로 사용.
        val effectiveMediaType = if (editedRenderJobId != null) "AUDIO" else mediaType

        val jobId = autoSubtitleRepository.submit(
            sourceUri = sourceUri,
            mediaType = effectiveMediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCodes = targetLanguageCodes,
            numberOfSpeakers = numberOfSpeakers,
            editedRenderJobId = editedRenderJobId,
        ).getOrElse { e ->
            markFailed(project, "자막 요청 실패")
            throw e
        }

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
            // "원본만" 모드 (targetLanguageCodes=[]) 또는 사용자가 명시적으로 원본 자막 선택
            // (includeOriginalLanguage=true) 시 originalSrt 를 lang="" 로 저장 — SaveAllVariants
            // 가 SUB_원본_ variant 노출/burn 대상으로 사용.
            if ((targetLanguageCodes.isEmpty() || includeOriginalLanguage) && !containsKey("")) {
                put("", ready.originalSrtUrl)
            }
        }
        // 번역 lang 을 먼저 처리하고, 원본 lang="" 은 결과를 본 뒤 저장 여부 결정.
        // 사용자가 원본 + 번역을 함께 골랐는데 번역이 모두 실패하면, 부산물로 만들어진
        // 원본 lang="" 도 저장하지 않음 (chip 영구 disabled + 미리보기 chip 등장 방지).
        // 번역 lang 자체가 없는 "원본만" 모드 (targetLanguageCodes 비어있음) 는 lang="" 가
        // 유일 산출물이라 그대로 저장.
        suspend fun saveLang(lang: String, url: String): Int {
            val body = autoSubtitleRepository.fetchSrt(url).getOrElse { e ->
                println("[GenerateAutoSubtitles] fetchSrt $lang failed: ${e.message}")
                return 0
            }
            val cues = runCatching { SrtParser.parse(body) }.getOrElse {
                println("[GenerateAutoSubtitles] parse $lang failed: ${it.message}")
                return 0
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
            return rows.size
        }
        var totalCues = 0
        var translationSucceeded = 0
        srtByLang.filterKeys { it.isNotBlank() }.forEach { (lang, url) ->
            val added = saveLang(lang, url)
            if (added > 0) translationSucceeded += 1
            totalCues += added
        }
        val originalUrl = srtByLang[""]
        if (originalUrl != null && (targetLanguageCodes.isEmpty() || translationSucceeded > 0)) {
            totalCues += saveLang("", originalUrl)
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
