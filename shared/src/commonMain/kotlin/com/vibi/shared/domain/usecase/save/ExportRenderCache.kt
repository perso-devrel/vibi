package com.vibi.shared.domain.usecase.save

import com.vibi.shared.platform.fileExists

/**
 * export 렌더 결과 캐시 — 편집 상태가 동일하면 직전 렌더 산출물을 재사용해 저장/공유의 중복 렌더를 막는다.
 *
 * 렌더 출력은 편집 상태(segments·bgm·separation·frame)의 순수 함수다. 따라서 시그니처가 같고 산출물
 * 파일이 아직 남아 있으면 그대로 재사용해도 결과가 동일하다(정확성 보장). 편집이 바뀌면 시그니처가
 * 달라져 자동 무효화되므로 "오래된 결과를 잘못 재사용"할 위험이 없다.
 *
 * 프로세스 내 단일 엔트리만 보관 — 노리는 시나리오(연속된 공유→저장, 반복 export)는 직전 1건이면 충분.
 * usecase 호출이 viewModelScope 단일 흐름이라 별도 동기화 없이 안전(동시 save+share 는 VM 의 status
 * 가드로 사실상 직렬화됨). 산출물 파일은 OS 캐시라 evict 될 수 있어 [get] 이 매번 존재를 확인한다.
 */
class ExportRenderCache(
    /** 산출물 파일 존재 확인. 기본은 플랫폼 [fileExists]; 테스트에서 주입 가능. */
    private val pathExists: (String) -> Boolean = ::fileExists,
) {
    private var signature: String? = null
    private var outputPath: String? = null

    /** [signature] 와 일치하고 산출물 파일이 여전히 존재하면 그 경로, 아니면 null(→ 재렌더). */
    fun get(signature: String): String? {
        if (signature != this.signature) return null
        val path = outputPath ?: return null
        if (!pathExists(path)) {
            // OS 캐시에서 제거됨 — 무효화하고 miss 처리.
            invalidate()
            return null
        }
        return path
    }

    fun put(signature: String, outputPath: String) {
        this.signature = signature
        this.outputPath = outputPath
    }

    fun invalidate() {
        signature = null
        outputPath = null
    }
}
