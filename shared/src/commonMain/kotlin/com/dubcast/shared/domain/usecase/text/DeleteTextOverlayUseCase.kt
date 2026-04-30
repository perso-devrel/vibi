package com.dubcast.shared.domain.usecase.text

import com.dubcast.shared.domain.repository.TextOverlayRepository

class DeleteTextOverlayUseCase constructor(
    private val textOverlayRepository: TextOverlayRepository
) {
    suspend operator fun invoke(overlayId: String) {
        textOverlayRepository.deleteOverlay(overlayId)
    }
}
