package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.repository.TextOverlayRepository
import com.example.dubcast.domain.util.isValidHexColor
import javax.inject.Inject

class UpdateTextOverlayUseCase @Inject constructor(
    private val textOverlayRepository: TextOverlayRepository
) {
    suspend operator fun invoke(
        overlayId: String,
        text: String? = null,
        fontFamily: String? = null,
        fontSizeSp: Float? = null,
        colorHex: String? = null,
        startMs: Long? = null,
        endMs: Long? = null,
        xPct: Float? = null,
        yPct: Float? = null,
        lane: Int? = null
    ): TextOverlay {
        val current = textOverlayRepository.getOverlay(overlayId)
            ?: throw IllegalArgumentException("Text overlay not found: $overlayId")
        text?.let { require(it.isNotBlank()) { "text must not be blank" } }
        fontFamily?.let {
            require(it in TextOverlay.SUPPORTED_FONT_FAMILIES) {
                "unsupported fontFamily: $it"
            }
        }
        fontSizeSp?.let {
            require(it in TextOverlay.MIN_FONT_SIZE_SP..TextOverlay.MAX_FONT_SIZE_SP) {
                "fontSizeSp out of range: $it"
            }
        }
        colorHex?.let {
            require(isValidHexColor(it)) {
                "colorHex must be #RRGGBB or #AARRGGBB: $it"
            }
        }
        val newStart = startMs ?: current.startMs
        val newEnd = endMs ?: current.endMs
        require(newEnd > newStart) { "endMs must be greater than startMs" }
        val updated = current.copy(
            text = text ?: current.text,
            fontFamily = fontFamily ?: current.fontFamily,
            fontSizeSp = fontSizeSp ?: current.fontSizeSp,
            colorHex = colorHex ?: current.colorHex,
            startMs = newStart,
            endMs = newEnd,
            xPct = xPct?.coerceIn(0f, 100f) ?: current.xPct,
            yPct = yPct?.coerceIn(0f, 100f) ?: current.yPct,
            lane = lane?.coerceAtLeast(0) ?: current.lane
        )
        textOverlayRepository.updateOverlay(updated)
        return updated
    }
}
