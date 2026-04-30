package com.dubcast.shared.domain.usecase.draft

import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.platform.currentTimeMillis

/**
 * 메인 화면 "이어서 작업" drafts 7일 만료 cleanup. InputViewModel.init 에서 fire-and-forget.
 *
 * threshold = 현재 시각 - 7일. updatedAt 이 그보다 작으면 cascade 삭제.
 * 자동 저장으로 사용자가 편집 즉시 updatedAt 이 갱신되므로 작업 중인 project 는 만료되지 않음.
 */
class ExpireOldDraftsUseCase(
    private val repository: EditProjectRepository,
) {
    suspend operator fun invoke() {
        val threshold = currentTimeMillis() - SEVEN_DAYS_MS
        repository.expireOldDrafts(threshold)
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
