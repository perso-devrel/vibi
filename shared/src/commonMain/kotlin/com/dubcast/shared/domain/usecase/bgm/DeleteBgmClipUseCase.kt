package com.dubcast.shared.domain.usecase.bgm

import com.dubcast.shared.domain.repository.BgmClipRepository

class DeleteBgmClipUseCase constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        bgmClipRepository.deleteClip(clipId)
    }
}
