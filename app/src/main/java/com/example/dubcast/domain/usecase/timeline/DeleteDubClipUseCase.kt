package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.repository.DubClipRepository
import java.io.File
import javax.inject.Inject

class DeleteDubClipUseCase @Inject constructor(
    private val dubClipRepository: DubClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        val clip = dubClipRepository.getClip(clipId)
        if (clip != null) {
            val audioFile = File(clip.audioFilePath)
            if (audioFile.exists()) {
                audioFile.delete()
            }
            dubClipRepository.deleteClip(clipId)
        }
    }
}
