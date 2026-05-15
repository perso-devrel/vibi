package com.vibi.cmp.ui.timeline.sounddeck

import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.ui.timeline.stemDisplayLabelFromId

/**
 * SoundDeck 카드 한 장의 표현 모델. directive(구간) 단위로 그룹핑된 [SoundDeckGroup] 안에 들어간다.
 * 구간 정보(시간 범위)는 그룹 헤더에 모이고, 카드 자신의 [rangeStartMs]/[rangeEndMs] 는 BGM 처럼
 * 자체 범위가 따로 있는 경우에만 채운다 — stem 카드는 모두 null.
 */
data class SoundCardModel(
    val key: String,
    val label: String,
    val kind: SoundCardKind,
    val source: SoundCardSource,
    val selected: Boolean,
    val volume: Float,
    /** BGM 카드의 다듬기 패널에서 속도 슬라이더 초기값으로 쓰임. stem 카드는 미사용. */
    val speed: Float = 1f,
    val audioUrl: String?,
    val rangeStartMs: Long?,
    val rangeEndMs: Long?,
)

enum class SoundCardKind { SPEAKER, VOICE_ALL, BACKGROUND, OTHER_STEM, BGM }

sealed class SoundCardSource {
    data class SeparationStem(val directiveId: String, val stemId: String) : SoundCardSource()
    data class Bgm(val clipId: String) : SoundCardSource()
}

/**
 * SoundDeck 의 그룹 단위. 음성분리는 구간(directive) 별로 한 그룹 — 같은 directive 의
 * 화자/배경음 stem 카드들을 함께 묶어 사용자가 어느 구간 결과인지 한눈에 알 수 있게 한다.
 * BGM 은 모든 클립을 한 그룹에 모은다.
 */
sealed class SoundDeckGroup {
    abstract val key: String
    abstract val cards: List<SoundCardModel>

    data class Separation(
        val directiveId: String,
        val index: Int,
        val rangeStartMs: Long,
        val rangeEndMs: Long,
        override val cards: List<SoundCardModel>,
    ) : SoundDeckGroup() {
        override val key: String get() = "sep:$directiveId"
    }

    data class Bgm(
        override val cards: List<SoundCardModel>,
    ) : SoundDeckGroup() {
        override val key: String get() = "bgm"
    }
}

private fun stemKindFor(stemId: String): SoundCardKind = when (Stem.kindFromId(stemId)) {
    com.vibi.shared.domain.model.StemKind.BACKGROUND -> SoundCardKind.BACKGROUND
    com.vibi.shared.domain.model.StemKind.VOICE_ALL -> SoundCardKind.VOICE_ALL
    com.vibi.shared.domain.model.StemKind.SPEAKER -> SoundCardKind.SPEAKER
    com.vibi.shared.domain.model.StemKind.UNKNOWN -> SoundCardKind.OTHER_STEM
}

private fun kindOrder(k: SoundCardKind): Int = when (k) {
    SoundCardKind.SPEAKER -> 0
    SoundCardKind.VOICE_ALL -> 1
    SoundCardKind.BACKGROUND -> 2
    SoundCardKind.OTHER_STEM -> 3
    SoundCardKind.BGM -> 4
}

/**
 * BGM 카드 label — sourceUri 의 마지막 path segment (확장자 제외) 를 사용해 사용자가 삽입한
 * 음원의 실제 이름이 카드에 노출되게 한다.
 *
 * - 녹음 파일은 `rec_<currentTimeMillis>.m4a` (AudioRecorder.ios.kt) → "녹음"
 * - Android picker 의 구버전 자동 이름 `audio_<currentTimeMillis>.<ext>` → "음원"
 * - Android picker 의 신규 이름 `<원본>_<currentTimeMillis>.<ext>` → 접미사 제거 후 원본 노출.
 *   timestamp 가 13자리 이상이라 일반 파일명과 충돌 가능성 낮음.
 */
internal fun bgmDisplayLabel(sourceUri: String): String {
    val lastSegment = sourceUri.substringAfterLast('/')
    val withoutExt = lastSegment.substringBeforeLast('.', missingDelimiterValue = lastSegment)
    if (withoutExt.matches(Regex("rec_\\d+"))) return "녹음"
    if (withoutExt.matches(Regex("audio_\\d+"))) return "음원"
    val stripped = withoutExt.replace(Regex("_\\d{13,}$"), "")
    return stripped.ifBlank { "음원" }
}

/**
 * 분리 directive 는 timeline 위치([SeparationDirective.rangeStartMs]) 순으로 정렬해 1-based 인덱스를
 * 부여하고, 각 그룹 내부에서는 stem 종류(화자→배경) 순으로 정렬한다.
 *
 * VOICE_ALL ("모든 화자") stem 은 UI 에서 제외 — 화자별 SPEAKER stem 으로 분리되므로 중복.
 * 데이터 layer 에선 [Stem.kindFromId] 결과에 따라 default selected=false 처리.
 *
 * BGM 은 별도 그룹으로 마지막에 둔다.
 */
fun buildSoundDeckGroups(
    separations: List<SeparationDirective>,
    bgmClips: List<BgmClip>,
): List<SoundDeckGroup> {
    val separationGroups = separations
        .sortedBy { it.rangeStartMs }
        .mapIndexed { idx, dir ->
            val cards = dir.selections
                .filterNot { Stem.kindFromId(it.stemId) == com.vibi.shared.domain.model.StemKind.VOICE_ALL }
                .map { sel ->
                    SoundCardModel(
                        key = "stem:${dir.id}:${sel.stemId}",
                        label = stemDisplayLabelFromId(sel.stemId),
                        kind = stemKindFor(sel.stemId),
                        source = SoundCardSource.SeparationStem(dir.id, sel.stemId),
                        selected = sel.selected,
                        volume = sel.volume,
                        audioUrl = sel.audioUrl,
                        rangeStartMs = null,
                        rangeEndMs = null,
                    )
                }
                .sortedWith(compareBy({ kindOrder(it.kind) }, { it.label }))
            SoundDeckGroup.Separation(
                directiveId = dir.id,
                index = idx + 1,
                rangeStartMs = dir.rangeStartMs,
                rangeEndMs = dir.rangeEndMs,
                cards = cards,
            )
        }
    val bgmCards = bgmClips
        .sortedBy { it.startMs }
        .map { bgm ->
            SoundCardModel(
                key = "bgm:${bgm.id}",
                label = bgmDisplayLabel(bgm.sourceUri),
                kind = SoundCardKind.BGM,
                source = SoundCardSource.Bgm(bgm.id),
                selected = bgm.volumeScale > 0f,
                volume = bgm.volumeScale,
                speed = bgm.speedScale,
                audioUrl = bgm.sourceUri,
                rangeStartMs = bgm.startMs,
                rangeEndMs = bgm.startMs + bgm.effectiveDurationMs,
            )
        }
    val bgmGroup = if (bgmCards.isEmpty()) emptyList()
                   else listOf(SoundDeckGroup.Bgm(bgmCards))
    return separationGroups + bgmGroup
}
