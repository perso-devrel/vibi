package com.example.dubcast.domain.usecase.subtitle

import android.util.Log
import com.example.dubcast.domain.model.AutoJobStatus
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.SubtitleSource
import com.example.dubcast.domain.repository.AutoSubtitleRepository
import com.example.dubcast.domain.repository.AutoSubtitleStatus
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import java.util.UUID
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_ATTEMPTS = 360 // ~30 min at 5s
private const val TAG = "GenerateAutoSubs"

class GenerateAutoSubtitlesUseCase @Inject constructor(
    private val autoSubtitleRepository: AutoSubtitleRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val editProjectRepository: EditProjectRepository
) {
    /**
     * Submits the source media to the BFF, polls until the SRT pipeline is
     * READY, downloads the (translated when available, else original) SRT,
     * parses cues into [SubtitleClip] rows, and persists them.
     *
     * Updates [com.example.dubcast.domain.model.EditProject.autoSubtitleStatus]
     * at each transition so the UI can render progress.
     */
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String?,
        numberOfSpeakers: Int = 1
    ): Result<Int> = runCatching {
        var project = editProjectRepository.getProject(projectId)
            ?: throw IllegalStateException("Project not found: $projectId")

        // Mark RUNNING and clear any previous error before submitting.
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
            targetLanguageCode = targetLanguageCode,
            numberOfSpeakers = numberOfSpeakers
        ).getOrElse { e ->
            Log.w(TAG, "submit failed", e)
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

        val srtUrl = ready.translatedSrtUrl ?: ready.originalSrtUrl
        val srtBody = autoSubtitleRepository.fetchSrt(srtUrl).getOrElse { e ->
            Log.w(TAG, "SRT download failed", e)
            markFailed(project, "자막 다운로드 실패")
            throw e
        }

        // Wipe any previous AUTO cues before inserting new ones — re-runs
        // (e.g. user changed target language and retried) must not stack.
        // Manual cues authored by the user are left untouched.
        subtitleClipRepository.deleteAutoSubtitles(projectId)

        val cues = runCatching { SrtParser.parse(srtBody) }.getOrElse { e ->
            Log.w(TAG, "SRT parse failed", e)
            markFailed(project, "자막 파싱 실패")
            throw e
        }
        val clipRows = cues.map { cue ->
            SubtitleClip(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                text = cue.text,
                startMs = cue.startMs,
                endMs = cue.endMs,
                position = SubtitlePosition(),
                source = SubtitleSource.AUTO
            )
        }
        subtitleClipRepository.addClips(clipRows)

        editProjectRepository.updateProject(
            project.copy(
                autoSubtitleStatus = AutoJobStatus.READY,
                autoSubtitleError = null
            )
        )
        cues.size
    }.onFailure { e ->
        Log.w(TAG, "auto-subtitles failed", e)
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

    /** UI-safe message; raw server / IO text stays in logs only. */
    private fun sanitizeMessage(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "자막 실패"
        else -> "자막 실패"
    }
}
