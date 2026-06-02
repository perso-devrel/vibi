package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.domain.repository.SegmentRepository

/**
 * 세그먼트를 [targetIndex] 위치로 이동(드래그 재정렬) 후 전체 `order` 를 0..n-1 로 연속 재배치.
 *
 * directive(음원분리)는 세그먼트에 앵커돼 있어(글로벌 range 가 파생값) order 만 바꾸면 자동으로 따라간다
 * — 호출자(VM)가 이동 후 글로벌 range 캐시를 재계산한다. 본 use case 는 세그먼트 order 만 책임.
 */
class MoveSegmentUseCase(
    private val segmentRepository: SegmentRepository,
) {
    /**
     * [segmentId] 를 order 정렬 리스트에서 [targetIndex](제거 후 재삽입 기준, append 허용) 위치로 이동.
     * 같은 위치(또는 못 찾음)면 no-op 으로 null 반환. 이동된 세그먼트의 새 order 반환.
     * 변경된 row 만 persist 해 불필요한 Room emit 을 줄인다.
     */
    suspend operator fun invoke(segmentId: String, targetIndex: Int): Int? {
        val projectId = segmentRepository.getSegment(segmentId)?.projectId ?: return null
        val ordered = segmentRepository.getByProjectId(projectId).sortedBy { it.order }
        val fromIndex = ordered.indexOfFirst { it.id == segmentId }
        if (fromIndex < 0) return null

        val moved = ordered[fromIndex]
        val without = ordered.toMutableList().also { it.removeAt(fromIndex) }
        without.add(targetIndex.coerceIn(0, without.size), moved)

        // 결과 순서가 동일하면 no-op.
        if (without.map { it.id } == ordered.map { it.id }) return null

        without.forEachIndexed { i, seg ->
            if (seg.order != i) segmentRepository.updateSegment(seg.copy(order = i))
        }
        return without.indexOfFirst { it.id == segmentId }
    }
}
