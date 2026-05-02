package com.dubcast.shared.platform

/**
 * 영상 한 프레임을 JPEG 로 추출 → 앱 cacheDir 에 저장하고 절대 경로 반환.
 *
 * 같은 source URI 에 대해선 cache hit 시 추출 생략. 호출자(예: InputViewModel) 가 결과 path 를
 * Coil `AsyncImage` model 로 그대로 전달 → 자동 memory cache + decode.
 *
 * Compose Multiplatform 의 VideoPlayer 다중 인스턴스 대안 — drafts N개 카드마다 ExoPlayer/AVPlayer
 * 띄우는 비용을 0 에 가깝게 (1프레임 디코드 + JPEG 압축 1회).
 */
interface VideoThumbnailExtractor {
    /**
     * @param uri 비디오 source URI / 절대 경로
     * @param atMs 추출 시점 (기본 0 = 첫 프레임)
     * @return 추출된 JPEG 파일의 절대 경로. 실패 시 null.
     */
    suspend fun extractThumbnail(uri: String, atMs: Long = 0L): String?
}
