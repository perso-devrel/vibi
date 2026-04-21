package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.repository.TextOverlayRepository
import java.util.UUID
import javax.inject.Inject

class DuplicateTextOverlayUseCase @Inject constructor(
    private val textOverlayRepository: TextOverlayRepository
) {
    suspend operator fun invoke(overlayId: String): TextOverlay {
        val source = textOverlayRepository.getOverlay(overlayId)
            ?: throw IllegalArgumentException("Text overlay not found: $overlayId")
        val duration = source.endMs - source.startMs
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            startMs = source.endMs,
            endMs = source.endMs + duration
        )
        textOverlayRepository.addOverlay(duplicate)
        return duplicate
    }
}
