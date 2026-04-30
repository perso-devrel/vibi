package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * BFF `/api/v2/testdata/separation/list` 응답 — 폴더 이름이 `<startSec>-<endSec>` 형식.
 * 각 폴더 안의 mp3 파일 이름이 stem id 로 사용됨 (background, speaker1, speaker2 등).
 */
@Serializable
data class TestdataSeparationFolderDto(
    val folder: String,
    val startSec: Int,
    val endSec: Int,
    val stems: List<String>,
)
