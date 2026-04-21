package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.repository.TextOverlayRepository
import javax.inject.Inject

class DeleteTextOverlayUseCase @Inject constructor(
    private val textOverlayRepository: TextOverlayRepository
) {
    suspend operator fun invoke(overlayId: String) {
        textOverlayRepository.deleteOverlay(overlayId)
    }
}
