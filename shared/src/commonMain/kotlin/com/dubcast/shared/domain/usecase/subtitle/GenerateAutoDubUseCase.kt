package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.repository.AutoDubJobStatus
import com.dubcast.shared.domain.repository.AutoDubRepository
import com.dubcast.shared.domain.repository.EditProjectRepository

private const val POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_ATTEMPTS = 360

class GenerateAutoDubUseCase(
    private val autoDubRepository: AutoDubRepository,
    private val editProjectRepository: EditProjectRepository
) {
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

        // my_plan: 언어별 상태 — Map[lang] = RUNNING 으로 마킹
        project = project.copy(
            autoDubStatus = AutoJobStatus.RUNNING,
            autoDubError = null,
            autoDubJobId = null,
            autoDubStatusByLang = project.autoDubStatusByLang + (targetLanguageCode to AutoJobStatus.RUNNING),
            autoDubJobIdByLang = project.autoDubJobIdByLang - targetLanguageCode
        )
        editProjectRepository.updateProject(project)

        val jobId = autoDubRepository.submit(
            sourceUri = sourceUri,
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
            numberOfSpeakers = numberOfSpeakers
        ).getOrElse { e ->
            markFailed(project, targetLanguageCode, "더빙 요청 실패")
            throw e
        }

        project = project.copy(
            autoDubJobId = jobId,
            autoDubJobIdByLang = project.autoDubJobIdByLang + (targetLanguageCode to jobId)
        )
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

        val audioName = "autodub_${projectId}_${targetLanguageCode}_${jobId}.mp3"
        val localAudioPath = autoDubRepository.downloadDubbedAudio(
            audioUrl = ready.dubbedAudioUrl,
            outputFileName = audioName
        ).getOrElse { e ->
            markFailed(project, targetLanguageCode, "더빙 다운로드 실패")
            throw e
        }

        // BFF 가 mux 한 mp4 도 함께 받아 dubbedVideoPaths 에 저장 — 미리보기 swap 용.
        // mux 가 실패하면 dubbedVideoUrl 이 null 이라 downloadDubbedVideo 자체를 건너뜀.
        val localVideoPath: String? = ready.dubbedVideoUrl?.let { mp4Url ->
            val videoName = "autodub_${projectId}_${targetLanguageCode}_${jobId}.mp4"
            autoDubRepository.downloadDubbedVideo(mp4Url, videoName).getOrNull()
        }

        // 갱신된 project 를 다시 읽어 다른 언어 잡이 동시에 갱신한 변경사항을 반영
        val latest = editProjectRepository.getProject(projectId) ?: project
        val nextAudioPaths = latest.dubbedAudioPaths + (targetLanguageCode to localAudioPath)
        val nextVideoPaths = if (localVideoPath != null) {
            latest.dubbedVideoPaths + (targetLanguageCode to localVideoPath)
        } else latest.dubbedVideoPaths
        val nextStatusByLang = latest.autoDubStatusByLang + (targetLanguageCode to AutoJobStatus.READY)
        val allReady = nextStatusByLang.values.all { it == AutoJobStatus.READY }
        editProjectRepository.updateProject(
            latest.copy(
                dubbedAudioPath = localAudioPath,                     // legacy 호환
                dubbedAudioPaths = nextAudioPaths,
                dubbedVideoPaths = nextVideoPaths,
                autoDubStatusByLang = nextStatusByLang,
                autoDubStatus = if (allReady) AutoJobStatus.READY else AutoJobStatus.RUNNING,
                autoDubError = null
            )
        )
        localAudioPath
    }.onFailure { e ->
        editProjectRepository.getProject(projectId)?.let {
            markFailed(it, targetLanguageCode, sanitizeMessage(e))
        }
    }

    private suspend fun markFailed(project: EditProject, languageCode: String, message: String?) {
        editProjectRepository.updateProject(
            project.copy(
                autoDubStatus = AutoJobStatus.FAILED,
                autoDubError = message,
                autoDubStatusByLang = project.autoDubStatusByLang + (languageCode to AutoJobStatus.FAILED)
            )
        )
    }

    private fun sanitizeMessage(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "더빙 실패"
        else -> "더빙 실패"
    }
}
