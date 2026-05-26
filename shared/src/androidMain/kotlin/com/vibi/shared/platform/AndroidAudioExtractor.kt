package com.vibi.shared.platform

/**
 * iOS 우선 출시 — 안드로이드 분리 기능은 별도 작업에서 활성화. 본 stub 은 KMP 빌드가 깨지지
 * 않도록 actual 만 제공. UI (TimelineScreen entry button) + ViewModel + Repository 가 모두
 * [isSupported]=false 분기로 진입 자체를 차단하므로 [prepareSeparationAudio] 가 실제로 호출되는
 * 경로는 deep link 같은 미래의 다른 진입점뿐 — 그때도 typed exception 으로 떨어져 graceful.
 */
class AndroidAudioExtractor : AudioExtractor {
    override val isSupported: Boolean = false

    override suspend fun prepareSeparationAudio(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        startMs: Long?,
        endMs: Long?,
    ): PreparedAudio {
        throw AudioExtractException.Unknown("Android separation not yet supported")
    }
}
