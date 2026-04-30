package com.dubcast.shared.domain.usecase.text

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.TextOverlay
import com.dubcast.shared.domain.repository.TextOverlayRepository
import com.dubcast.shared.domain.util.isValidHexColor
import com.dubcast.shared.domain.util.pickLowestFreeLane
import kotlinx.coroutines.flow.first

class AddTextOverlayUseCase constructor(
    private val textOverlayRepository: TextOverlayRepository
) {
    suspend operator fun invoke(
        projectId: String,
        text: String,
        startMs: Long,
        endMs: Long,
        fontFamily: String = TextOverlay.DEFAULT_FONT_FAMILY,
        fontSizeSp: Float = TextOverlay.DEFAULT_FONT_SIZE_SP,
        colorHex: String = TextOverlay.DEFAULT_COLOR_HEX,
        xPct: Float = 50f,
        yPct: Float = 50f,
        lane: Int? = null
    ): TextOverlay {
        require(text.isNotBlank()) { "text must not be blank" }
        require(endMs > startMs) { "endMs ($endMs) must be greater than startMs ($startMs)" }
        require(fontFamily in TextOverlay.SUPPORTED_FONT_FAMILIES) {
            "unsupported fontFamily: $fontFamily"
        }
        require(fontSizeSp in TextOverlay.MIN_FONT_SIZE_SP..TextOverlay.MAX_FONT_SIZE_SP) {
            "fontSizeSp must be in [${TextOverlay.MIN_FONT_SIZE_SP}, ${TextOverlay.MAX_FONT_SIZE_SP}]"
        }
        require(isValidHexColor(colorHex)) {
            "colorHex must be #RRGGBB or #AARRGGBB: $colorHex"
        }
        val effectiveLane = lane ?: pickLowestFreeLane(
            existing = textOverlayRepository.observeOverlays(projectId).first(),
            startMs = startMs,
            endMs = endMs,
            laneOf = { it.lane },
            startOf = { it.startMs },
            endOf = { it.endMs }
        )
        val overlay = TextOverlay(
            id = generateId(),
            projectId = projectId,
            text = text,
            fontFamily = fontFamily,
            fontSizeSp = fontSizeSp,
            colorHex = colorHex,
            startMs = startMs,
            endMs = endMs,
            xPct = xPct.coerceIn(0f, 100f),
            yPct = yPct.coerceIn(0f, 100f),
            lane = effectiveLane
        )
        textOverlayRepository.addOverlay(overlay)
        return overlay
    }
}
