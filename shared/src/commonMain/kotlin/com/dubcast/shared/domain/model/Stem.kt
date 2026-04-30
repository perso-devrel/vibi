package com.dubcast.shared.domain.model

enum class StemKind {
    BACKGROUND,
    VOICE_ALL,
    SPEAKER,
    UNKNOWN
}

data class Stem(
    val stemId: String,
    val label: String,
    val url: String,
    val kind: StemKind,
    val speakerIndex: Int? = null
) {
    companion object {
        private const val SPEAKER_PREFIX = "speaker_"

        fun kindFromId(stemId: String): StemKind = when {
            stemId == "background" -> StemKind.BACKGROUND
            stemId == "voice_all" -> StemKind.VOICE_ALL
            stemId.startsWith(SPEAKER_PREFIX) -> StemKind.SPEAKER
            else -> StemKind.UNKNOWN
        }

        fun speakerIndexFromId(stemId: String): Int? {
            if (!stemId.startsWith(SPEAKER_PREFIX)) return null
            return stemId.removePrefix(SPEAKER_PREFIX).toIntOrNull()
        }
    }
}
