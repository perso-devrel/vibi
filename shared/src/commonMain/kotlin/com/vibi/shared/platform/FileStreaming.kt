package com.vibi.shared.platform

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel

/**
 * 대용량 미디어 OOM 방지용 스트리밍 파일 I/O. [readFileBytes]/[saveBytesToCache] 가 파일 전체를
 * ByteArray(+플랫폼 버퍼 복사) 로 적재해 100MB대 영상에서 피크 메모리가 2배로 치솟던 문제를,
 * 업로드/다운로드 모두 청크 스트리밍으로 대체한다.
 */

/**
 * 로컬 파일([path])을 스트리밍 업로드 바디로 노출 — 엔진이 청크 단위로 읽어 전송하므로 전체
 * ByteArray 적재가 없다. R2 presigned PUT 의 SigV4 가 contentType + Content-Length 를 sign 하므로
 * 둘 다 정확히 채운다(Content-Length=실제 파일 크기, contentType=[contentType]).
 */
expect suspend fun fileUploadBody(path: String, contentType: String): OutgoingContent

/** 응답 [channel] 을 [destPath] 파일로 청크 스트리밍 기록(전체 적재 없음). 기존 파일은 덮어쓴다. */
expect suspend fun writeChannelToFile(channel: ByteReadChannel, destPath: String)

/** 캐시 디렉터리 절대경로 (OS 가 저장 압박 시 evict 가능). 다운로드 목적지 경로 조합용. */
expect fun cacheDirPath(): String

/** 영구 stem 디렉터리 절대경로 (evict 안 됨). 없으면 생성. 오프라인 stem 저장 목적지용. */
expect fun persistentStemsDirPath(): String
