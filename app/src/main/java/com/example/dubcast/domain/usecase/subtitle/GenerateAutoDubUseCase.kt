package com.example.dubcast.domain.usecase.subtitle

import android.util.Log
import com.example.dubcast.domain.model.AutoJobStatus
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.repository.AutoDubJobStatus
import com.example.dubcast.domain.repository.AutoDubRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_ATTEMPTS = 360 // ~30 min at 5s
private const val TAG = "GenerateAutoDub"

class GenerateAutoDubUseCase @Inject constructor(
    private val autoDubRepository: AutoDubRepository,
    private val editProjectRepository: EditProjectRepository
) {
    /**
     * Submits the source media for auto-dubbing, polls until READY, downloads
     * the dubbed audio file, and writes its absolute path to
     * [com.example.dubcast.domain.model.EditProject.dubbedAudioPath]. The render
     * pipeline picks the dubbed file up from there in lieu of the original
     * segment audio.
     */
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int = 1
    ): Result<String> = runCatching {
        var project = editProjectRepository.getProject(projectId)
            ?: throw IllegalStateException("Project not found: $projectId")

        project = project.copy(
            autoDubStatus = AutoJobStatus.RUNNING,
            autoDubError = null,
            autoDubJobId = null
        )
        editProjectRepository.updateProject(project)

        val jobId = autoDubRepository.submit(
            sourceUri = sourceUri,
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
            numberOfSpeakers = numberOfSpeakers
        ).getOrElse { e ->
            Log.w(TAG, "submit failed", e)
            markFailed(project, "더빙 요청 실패")
            throw e
        }

        project = project.copy(autoDubJobId = jobId)
        editProjectRepository.updateProject(project)

        val ready = pollUntilReady(
            label = "dub",
            pollIntervalMs = POLL_INTERVAL_MS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            fetch = { autoDubRepository.pollStatus(jobId).getOrThrow() }
        ) { status ->
            when (status) {
                is AutoDubJobStatus.Ready -> PollDecision.Ready(status)
                is AutoDubJobStatus.Failed -> PollDecision.Failed(status.reason)
                is AutoDubJobStatus.Processing -> PollDecision.Processing
            }
        }

        val outputName = "autodub_${projectId}_${jobId}.mp3"
        val localPath = autoDubRepository.downloadDubbedAudio(
            audioUrl = ready.dubbedAudioUrl,
            outputFileName = outputName
        ).getOrElse { e ->
            Log.w(TAG, "download failed", e)
            markFailed(project, "더빙 다운로드 실패")
            throw e
        }

        editProjectRepository.updateProject(
            project.copy(
                dubbedAudioPath = localPath,
                autoDubStatus = AutoJobStatus.READY,
                autoDubError = null
            )
        )
        localPath
    }.onFailure { e ->
        Log.w(TAG, "auto-dub failed", e)
        editProjectRepository.getProject(projectId)?.let {
            markFailed(it, sanitizeMessage(e))
        }
    }

    private suspend fun markFailed(project: EditProject, message: String?) {
        editProjectRepository.updateProject(
            project.copy(
                autoDubStatus = AutoJobStatus.FAILED,
                autoDubError = message
            )
        )
    }

    /** UI-safe message; raw server / IO text stays in logs only. */
    private fun sanitizeMessage(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "더빙 실패"
        else -> "더빙 실패"
    }
}
