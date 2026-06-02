package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.isContiguousMergeableRun
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository

/**
 * 인접·이어붙일 수 있는 영상 세그먼트들을 하나로 병합. 음원분리(단일 세그먼트 전용)를 split 된 여러 조각에
 * 걸쳐 적용할 때, 먼저 합쳐 깨끗한 단일 세그먼트로 만드는 데 쓴다.
 *
 * 병합 조건 (하나라도 위배 시 null = 병합 불가): 연속 order · 동일 sourceUri · 연속 trim
 * (앞 조각 effectiveTrimEnd == 뒤 조각 trimStart) · 동일 speed/volume · 복제본 아님(duplicatedFromId 없음).
 * → split 으로 쪼갠 조각들은 충족, 복제본/다른 영상은 불가.
 *
 * 병합 시 첫 조각(head)의 trimEnd 를 마지막 조각 effectiveTrimEnd 로 확장하고 나머지는 삭제, order 를
 * 0..n-1 로 재배치. 삭제되는 조각에 앵커된 directive 는 head 로 재앵커(local 좌표는 같은 source 라 그대로).
 */
class MergeSegmentsUseCase(
    private val segmentRepository: SegmentRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
) {
    suspend operator fun invoke(segmentIds: List<String>): Segment? {
        val idSet = segmentIds.toSet()
        if (idSet.size < 2) return null
        val anchor = segmentRepository.getSegment(segmentIds.first()) ?: return null
        val run = segmentRepository.getByProjectId(anchor.projectId)
            .filter { it.id in idSet }
            .sortedBy { it.order }
        if (!run.isContiguousMergeableRun()) return null

        val head = run.first()
        val tail = run.last()
        val merged = head.copy(trimEndMs = tail.effectiveTrimEndMs)
        segmentRepository.updateSegment(merged)

        // 삭제되는 조각의 directive → head 로 재앵커 (local 좌표 동일 source 라 그대로 유효).
        val removedIds = run.drop(1).map { it.id }.toSet()
        val orphanDirectives = separationDirectiveRepository.getByProject(head.projectId)
            .filter { it.segmentId in removedIds }
        if (orphanDirectives.isNotEmpty()) {
            separationDirectiveRepository.addAll(orphanDirectives.map { it.copy(segmentId = head.id) })
        }
        removedIds.forEach { segmentRepository.deleteSegment(it) }

        // order 0..n-1 연속 재배치.
        val remaining = segmentRepository.getByProjectId(head.projectId).sortedBy { it.order }
        remaining.forEachIndexed { i, s ->
            if (s.order != i) segmentRepository.updateSegment(s.copy(order = i))
        }
        return merged
    }
}
