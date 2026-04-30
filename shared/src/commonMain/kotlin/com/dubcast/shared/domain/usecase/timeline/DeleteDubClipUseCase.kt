package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.repository.DubClipRepository
import com.dubcast.shared.platform.deleteLocalFile
import com.dubcast.shared.platform.fileExists

class DeleteDubClipUseCase(
    private val dubClipRepository: DubClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        val clip = dubClipRepository.getClip(clipId)
        if (clip != null) {
            if (fileExists(clip.audioFilePath)) {
                deleteLocalFile(clip.audioFilePath)
            }
            dubClipRepository.deleteClip(clipId)
        }
    }
}
