package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.repository.SubtitleClipRepository

class AddSubtitleClipUseCase constructor(
    private val subtitleClipRepository: SubtitleClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        text: String,
        startMs: Long,
        endMs: Long,
        position: SubtitlePosition,
        fontFamily: String = SubtitleClip.DEFAULT_FONT_FAMILY,
        fontSizeSp: Float = SubtitleClip.DEFAULT_FONT_SIZE_SP,
        colorHex: String = SubtitleClip.DEFAULT_COLOR_HEX,
        backgroundColorHex: String = SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX,
    ): SubtitleClip {
        require(endMs > startMs) { "endMs must be greater than startMs" }
        val clip = SubtitleClip(
            id = generateId(),
            projectId = projectId,
            text = text,
            startMs = startMs,
            endMs = endMs,
            position = position,
            fontFamily = fontFamily,
            fontSizeSp = fontSizeSp,
            colorHex = colorHex,
            backgroundColorHex = backgroundColorHex,
        )
        subtitleClipRepository.addClip(clip)
        return clip
    }
}
