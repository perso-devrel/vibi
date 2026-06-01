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
    /** SPEAKER 카드만 — 1-based. SoundCard chip + 타임라인 파형 highlight 가 같은 팔레트로 매핑. */
    val speakerIndex: Int? = null,
    /**
     * BGM 카드만 — createdAt(추가 순서) 정렬 1-based. SoundCard chip + 타임라인 클립 블록이 같은
     * BgmPalette cycle 로 매핑. **startMs(위치) 가 아니라 createdAt 정렬을 쓰는 이유**: 사용자가
     * 클립을 drag 해 위치를 바꿔도 색이 흔들리지 않게 — "처음 지정된 색은 변하지 않음".
     */
    val bgmIndex: Int? = null,
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

internal fun isRecordingSourceUri(sourceUri: String): Boolean {
    val name = sourceUri.substringAfterLast('/').substringBeforeLast('.')
    return name.startsWith("recording_", ignoreCase = true) ||
        name.startsWith("rec_", ignoreCase = true) ||
        sourceUri.contains("/recordings/", ignoreCase = true)
}

/**
 * BGM 카드 label — sourceUri 의 마지막 path segment (확장자 제외) 를 사용해 사용자가 삽입한
 * 음원의 실제 이름이 카드에 노출되게 한다.
 *
 * - 녹음은 [recordingOrdinal] 가 있으면 "녹음N" (예: "녹음1"), 없으면 "녹음".
 *   호출부는 보통 추가 순서 (createdAt) 기준 ordinal 을 1 부터 부여.
 * - Android picker 구버전 자동 이름 `audio_<ts>.<ext>` → "음원"
 * - Android picker 신규 이름 `<원본>_<ts>.<ext>` → 접미사 제거 후 원본 노출.
 */
internal fun bgmDisplayLabel(sourceUri: String, recordingOrdinal: Int? = null): String {
    if (isRecordingSourceUri(sourceUri)) {
        return if (recordingOrdinal != null) "Recording $recordingOrdinal" else "Recording"
    }
    val lastSegment = sourceUri.substringAfterLast('/')
    val withoutExt = lastSegment.substringBeforeLast('.', missingDelimiterValue = lastSegment)
    if (withoutExt.matches(Regex("audio_\\d+"))) return "Audio"
    val stripped = withoutExt.replace(Regex("_\\d{13,}$"), "")
    return stripped.ifBlank { "Audio" }
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
                        speakerIndex = Stem.speakerIndexFromId(sel.stemId),
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
    // 카드 표시 순서 — timeline 위치(startMs) 기준 정렬. 사용자가 카드를 좌→우로 훑을 때
    // 실제 영상 흐름 순으로 보이게.
    val sortedBgm = bgmClips.sortedBy { it.startMs }
    // 색·녹음 번호는 추가 순서(createdAt) 기준 stable 1-based — 위치(startMs) 가 바뀌어도
    // 절대 변하지 않게. createdAt 동률이면 id 로 안정 정렬.
    val sortedByCreation = bgmClips.sortedWith(compareBy({ it.createdAt }, { it.id }))
    val bgmIndexByClipId: Map<String, Int> = sortedByCreation
        .withIndex()
        .associate { (i, b) -> b.id to (i + 1) }
    val recordingOrdinal: Map<String, Int> = sortedByCreation
        .filter { isRecordingSourceUri(it.sourceUri) }
        .withIndex()
        .associate { (i, b) -> b.id to (i + 1) }
    val bgmCards = sortedBgm
        .map { bgm ->
            SoundCardModel(
                key = "bgm:${bgm.id}",
                // 사용자가 지정한 이름이 있으면 그것을, 없으면 파일명/"Recording N" 자동 라벨.
                label = bgm.customName?.takeIf { it.isNotBlank() }
                    ?: bgmDisplayLabel(bgm.sourceUri, recordingOrdinal[bgm.id]),
                kind = SoundCardKind.BGM,
                source = SoundCardSource.Bgm(bgm.id),
                selected = bgm.volumeScale > 0f,
                volume = bgm.volumeScale,
                speed = bgm.speedScale,
                audioUrl = bgm.sourceUri,
                rangeStartMs = bgm.startMs,
                rangeEndMs = bgm.startMs + bgm.effectiveDurationMs,
                bgmIndex = bgmIndexByClipId[bgm.id],
            )
        }
    val bgmGroup = if (bgmCards.isEmpty()) emptyList()
                   else listOf(SoundDeckGroup.Bgm(bgmCards))
    return separationGroups + bgmGroup
}
