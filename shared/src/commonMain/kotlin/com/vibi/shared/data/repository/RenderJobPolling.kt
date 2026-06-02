package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * BFF 렌더 잡 상태를 적응형 백오프로 폴링하다 COMPLETED 면 반환, FAILED/타임아웃이면 throw.
 *
 * 폴링 간격은 [INITIAL_POLL_MS] 에서 시작해 매회 1.5배로 늘려 [MAX_POLL_MS] 에서 고정한다.
 * 고정 2초 폴링 대비: 짧은 렌더(작은 클립·서버 캐시 히트)는 완료를 sub-second 로 감지해 평균
 * 대기가 짧아지고, 긴 렌더는 몇 폴 만에 정상상태 2초로 수렴하므로 서버 요청 부하 증가가 없다.
 * 고정 2초의 본질 손해 — 잡이 실제 끝난 뒤 클라이언트가 알아채기까지 버려지던 최대 ~2초 — 를 줄이는 게 목적.
 *
 * progress 매핑은 호출자마다 구간이 달라 [onPoll] 로 위임 — 서버 progress(0..100)를 받아 보고한다.
 * [V3RenderExecutor] (iOS) / [RemoteRenderExecutor] (Android) 가 공유.
 */
internal suspend fun pollRenderJobUntilDone(
    api: BffApi,
    jobId: String,
    onPoll: (serverProgress: Int) -> Unit,
    onCompleted: () -> Unit,
) {
    val startTime = currentTimeMillis()
    var pollDelayMs = INITIAL_POLL_MS
    while (currentCoroutineContext().isActive) {
        if (currentTimeMillis() - startTime > MAX_POLL_TOTAL_MS) {
            throw RuntimeException("Render timed out (15 min)")
        }
        val status = api.getRenderStatus(jobId)
        when (status.status) {
            "COMPLETED" -> { onCompleted(); return }
            "FAILED" -> throw RuntimeException(status.error ?: "Server render failed")
            else -> onPoll(status.progress)
        }
        delay(pollDelayMs)
        pollDelayMs = (pollDelayMs * 3 / 2).coerceAtMost(MAX_POLL_MS)
    }
    throw CancellationException("Render polling cancelled")
}

private const val INITIAL_POLL_MS = 300L
private const val MAX_POLL_MS = 2000L
private const val MAX_POLL_TOTAL_MS = 15 * 60 * 1000L
