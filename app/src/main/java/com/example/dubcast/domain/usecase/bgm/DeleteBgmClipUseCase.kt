package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.domain.repository.BgmClipRepository
import javax.inject.Inject

class DeleteBgmClipUseCase @Inject constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        bgmClipRepository.deleteClip(clipId)
    }
}
