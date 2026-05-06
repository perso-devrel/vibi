package com.dubcast.shared.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE dub_jobs ADD COLUMN withDubbing INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS edit_projects (
                projectId TEXT NOT NULL PRIMARY KEY,
                videoUri TEXT NOT NULL,
                videoDurationMs INTEGER NOT NULL,
                videoWidth INTEGER NOT NULL,
                videoHeight INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS dub_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                text TEXT NOT NULL,
                voiceId TEXT NOT NULL,
                voiceName TEXT NOT NULL,
                audioFilePath TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                volume REAL NOT NULL DEFAULT 1.0
            )"""
        )
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS subtitle_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                text TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                anchor TEXT NOT NULL DEFAULT 'bottom',
                yOffsetPct REAL NOT NULL DEFAULT 90.0
            )"""
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS dub_jobs")
        connection.execSQL("DROP TABLE IF EXISTS subtitle_segments")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN trimStartMs INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN trimEndMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN sourceDubClipId TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN xPct REAL DEFAULT NULL")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN yPct REAL DEFAULT NULL")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN widthPct REAL DEFAULT NULL")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN heightPct REAL DEFAULT NULL")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS image_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                imageUri TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                xPct REAL NOT NULL DEFAULT 50.0,
                yPct REAL NOT NULL DEFAULT 50.0,
                widthPct REAL NOT NULL DEFAULT 30.0,
                heightPct REAL NOT NULL DEFAULT 30.0
            )"""
        )
    }
}

val MIGRATION_7_8_STATEMENTS: List<String> = listOf(
    """CREATE TABLE IF NOT EXISTS segments (
        id TEXT NOT NULL PRIMARY KEY,
        projectId TEXT NOT NULL,
        type TEXT NOT NULL,
        `order` INTEGER NOT NULL,
        sourceUri TEXT NOT NULL,
        durationMs INTEGER NOT NULL,
        width INTEGER NOT NULL,
        height INTEGER NOT NULL,
        trimStartMs INTEGER NOT NULL DEFAULT 0,
        trimEndMs INTEGER NOT NULL DEFAULT 0,
        imageXPct REAL NOT NULL DEFAULT 50.0,
        imageYPct REAL NOT NULL DEFAULT 50.0,
        imageWidthPct REAL NOT NULL DEFAULT 50.0,
        imageHeightPct REAL NOT NULL DEFAULT 50.0
    )""",
    """INSERT INTO segments(
        id, projectId, type, `order`, sourceUri,
        durationMs, width, height, trimStartMs, trimEndMs,
        imageXPct, imageYPct, imageWidthPct, imageHeightPct
    )
    SELECT projectId || '_seg0', projectId, 'VIDEO', 0, videoUri,
        videoDurationMs, videoWidth, videoHeight, trimStartMs, trimEndMs,
        50.0, 50.0, 50.0, 50.0
    FROM edit_projects""",
    """CREATE TABLE edit_projects_new (
        projectId TEXT NOT NULL PRIMARY KEY,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
    )""",
    """INSERT INTO edit_projects_new(projectId, createdAt, updatedAt)
    SELECT projectId, createdAt, updatedAt FROM edit_projects""",
    "DROP TABLE edit_projects",
    "ALTER TABLE edit_projects_new RENAME TO edit_projects"
)

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        MIGRATION_7_8_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE segments ADD COLUMN volumeScale REAL NOT NULL DEFAULT 1.0")
        connection.execSQL("ALTER TABLE segments ADD COLUMN speedScale REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN frameWidth INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN frameHeight INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN backgroundColorHex TEXT NOT NULL DEFAULT '#000000'")
        connection.execSQL(
            """UPDATE edit_projects SET
                frameWidth = COALESCE((
                    SELECT s.width FROM segments s
                    WHERE s.projectId = edit_projects.projectId AND s.type = 'VIDEO'
                    ORDER BY s.`order` ASC LIMIT 1
                ), 0),
                frameHeight = COALESCE((
                    SELECT s.height FROM segments s
                    WHERE s.projectId = edit_projects.projectId AND s.type = 'VIDEO'
                    ORDER BY s.`order` ASC LIMIT 1
                ), 0)"""
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS text_overlays (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                text TEXT NOT NULL,
                fontFamily TEXT NOT NULL DEFAULT 'noto_sans_kr',
                fontSizeSp REAL NOT NULL DEFAULT 24.0,
                colorHex TEXT NOT NULL DEFAULT '#FFFFFFFF',
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                xPct REAL NOT NULL DEFAULT 50.0,
                yPct REAL NOT NULL DEFAULT 50.0
            )"""
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS bgm_clips (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                sourceUri TEXT NOT NULL,
                sourceDurationMs INTEGER NOT NULL,
                startMs INTEGER NOT NULL,
                volumeScale REAL NOT NULL DEFAULT 1.0
            )"""
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE image_clips ADD COLUMN lane INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE text_overlays ADD COLUMN lane INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE segments ADD COLUMN duplicatedFromId TEXT")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN videoScale REAL NOT NULL DEFAULT 1.0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN videoOffsetXPct REAL NOT NULL DEFAULT 0.0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN videoOffsetYPct REAL NOT NULL DEFAULT 0.0")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN targetLanguageCode TEXT NOT NULL DEFAULT 'original'")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN enableAutoDubbing INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN enableAutoSubtitles INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN dubbedAudioPath TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoSubtitleStatus TEXT NOT NULL DEFAULT 'IDLE'")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoDubStatus TEXT NOT NULL DEFAULT 'IDLE'")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoSubtitleJobId TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoDubJobId TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoSubtitleError TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoDubError TEXT")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN numberOfSpeakers INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v20 — my_plan.md: 다중 언어 출력 + 편집화면 표시 옵션.
 *  - targetLanguageCodesJson: JSON array string ("[\"ko\",\"en\"]"). 빈 문자열이면 기존 targetLanguageCode 단일.
 *  - showSubtitlesOnPreview / showDubbingOnPreview: 편집 미리보기 토글.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN targetLanguageCodesJson TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN showSubtitlesOnPreview INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN showDubbingOnPreview INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v21 — my_plan: 언어별 자동 더빙 결과 (Map JSON).
 *  - dubbedAudioPathsJson: {lang -> mp3 path}
 *  - autoDubStatusByLangJson: {lang -> AutoJobStatus.name}
 *  - autoDubJobIdByLangJson: {lang -> jobId}
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN dubbedAudioPathsJson TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoDubStatusByLangJson TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN autoDubJobIdByLangJson TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v22 — my_plan: 음성분리 명세 테이블 신규.
 *
 * 사용자가 어느 언어 더빙 위에서 음성분리 sheet 를 띄웠든, 같은 명세는 1개 row 로 저장.
 * Export 시점에 모든 결과 영상(원본 + N 더빙)에 동일 적용.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS separation_directives (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                rangeStartMs INTEGER NOT NULL,
                rangeEndMs INTEGER NOT NULL,
                numberOfSpeakers INTEGER NOT NULL,
                muteOriginalSegmentAudio INTEGER NOT NULL,
                sourceMixJobId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
    }
}

/**
 * v23 — 미리보기용 dubbed video mp4 경로 맵.
 *  - dubbedVideoPathsJson: {lang -> mp4 path} (BFF 가 video+dubAudio mux 한 결과 path)
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN dubbedVideoPathsJson TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v24 — 자막 다국어: SubtitleClip 에 languageCode 추가. 기존 row 는 "" (원본/미지정).
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN languageCode TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v25 — 음성분리 directive 모델 변경: BFF mix 산출물(`sourceMixJobId`) 폐기 →
 * 사용자가 고른 stem (stemId + volume + audioUrl) 들을 JSON 으로 직접 보존.
 * 기존 row 는 stem URL 정보가 없어 더 이상 합성 불가능 — 빈 selections 로 보존.
 * (해당 row 는 지금까지 export 와 연결돼 있지 않았기에 손실 없음.)
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE separation_directives ADD COLUMN selectionsJson TEXT NOT NULL DEFAULT '[]'")
        // sourceMixJobId 컬럼은 SQLite ALTER ... DROP COLUMN 미지원 환경 호환을 위해
        // 컬럼만 남기지 않고 테이블 재생성. 기존 row 는 selections 비워서 보존.
        connection.execSQL(
            """CREATE TABLE separation_directives_new (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                rangeStartMs INTEGER NOT NULL,
                rangeEndMs INTEGER NOT NULL,
                numberOfSpeakers INTEGER NOT NULL,
                muteOriginalSegmentAudio INTEGER NOT NULL,
                selectionsJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        connection.execSQL(
            """INSERT INTO separation_directives_new (
                id, projectId, rangeStartMs, rangeEndMs,
                numberOfSpeakers, muteOriginalSegmentAudio, selectionsJson, createdAt
            ) SELECT id, projectId, rangeStartMs, rangeEndMs,
                numberOfSpeakers, muteOriginalSegmentAudio, '[]', createdAt
              FROM separation_directives"""
        )
        connection.execSQL("DROP TABLE separation_directives")
        connection.execSQL("ALTER TABLE separation_directives_new RENAME TO separation_directives")
    }
}

/**
 * v26 — 자막 스타일 필드: 폰트, 크기, 글자 색, 박스 배경색.
 * 기존 row 는 default 로 채움 (Noto Sans KR / 16sp / 흰색 / 반투명 검정).
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN fontFamily TEXT NOT NULL DEFAULT 'Noto Sans KR'")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN fontSizeSp REAL NOT NULL DEFAULT 16.0")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#FFFFFFFF'")
        connection.execSQL("ALTER TABLE subtitle_clips ADD COLUMN backgroundColorHex TEXT NOT NULL DEFAULT '#80000000'")
    }
}

/**
 * v27 — 음성분리 잡 영속화 필드. 자막/더빙처럼 jobId/status 를 EditProject 에 저장해 백그라운드
 * 진행 + 화면 재진입 시 자동 재개. 기존 row 는 default IDLE.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationJobId TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationSegmentId TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationNumberOfSpeakers INTEGER NOT NULL DEFAULT 2")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationMuteOriginal INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationStatus TEXT NOT NULL DEFAULT 'IDLE'")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN separationError TEXT")
    }
}

/**
 * v28 — 임시저장 제목. null fallback (UI 가 createdAt 포맷팅 사용).
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN title TEXT")
    }
}

/**
 * v29 — STT 검토 흐름 영속화: EditProject 에 pendingReviewTargetLangsCsv 추가.
 * 사용자가 timeline 떠났다 들어와도 "검토 대기" 자동 복귀 + target 언어 보존.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN pendingReviewTargetLangsCsv TEXT")
    }
}

/**
 * v30 — BgmClip 에 speedScale (재생 속도 0.25..4) 추가. 기존 row 는 1.0 (정상 속도) 로 채움.
 * 사용자가 추가한 음원 클립도 segment 와 동일한 볼륨/속도 조절 가능하도록.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE bgm_clips ADD COLUMN speedScale REAL NOT NULL DEFAULT 1.0")
    }
}

/**
 * v31 — 편집 영상 render 캐시 영속화.
 *  - currentRenderJobId: BFF 에 가장 최근에 제출한 render jobId (null = 아직 render 한 적 없음).
 *  - isRenderStale: 마지막 render 이후 timeline mutation 발생했는지. true 면 다음 자막/더빙/분리 작업 시
 *    EnsureLatestRenderUseCase 가 새로 render. 기존 row 는 default true (영상이 stale 하다고 가정해야 안전).
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN currentRenderJobId TEXT")
        connection.execSQL("ALTER TABLE edit_projects ADD COLUMN isRenderStale INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v32 — BFF audio-only render 모드 도입에 따른 jobId 슬롯 분리.
 *  - currentRenderJobId 컬럼 제거 (단일 슬롯 → 종류별 슬롯 2개로 대체).
 *  - currentAudioRenderJobId TEXT NULL — 자막/STT/음성분리 (audio-only m4a render 결과) 캐시.
 *  - currentVideoRenderJobId TEXT NULL — 자동 더빙 (full mp4 render 결과) 캐시.
 *
 * 마이그레이션: 기존 currentRenderJobId 값은 v31 시점에서 video render 결과 (당시 BFF 는 video 모드만)
 * 이므로 currentVideoRenderJobId 로 이관. audio 슬롯은 비움. SQLite ALTER ... DROP COLUMN 의 환경 호환을
 * 위해 테이블 재생성 패턴 사용 (CREATE new + INSERT SELECT + DROP old + RENAME).
 */
val MIGRATION_31_32_STATEMENTS: List<String> = listOf(
    """CREATE TABLE edit_projects_new (
        projectId TEXT NOT NULL PRIMARY KEY,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        title TEXT,
        pendingReviewTargetLangsCsv TEXT,
        frameWidth INTEGER NOT NULL DEFAULT 0,
        frameHeight INTEGER NOT NULL DEFAULT 0,
        backgroundColorHex TEXT NOT NULL DEFAULT '#000000',
        videoScale REAL NOT NULL DEFAULT 1.0,
        videoOffsetXPct REAL NOT NULL DEFAULT 0.0,
        videoOffsetYPct REAL NOT NULL DEFAULT 0.0,
        targetLanguageCode TEXT NOT NULL DEFAULT 'original',
        targetLanguageCodesJson TEXT NOT NULL DEFAULT '',
        enableAutoDubbing INTEGER NOT NULL DEFAULT 0,
        enableAutoSubtitles INTEGER NOT NULL DEFAULT 0,
        showSubtitlesOnPreview INTEGER NOT NULL DEFAULT 1,
        showDubbingOnPreview INTEGER NOT NULL DEFAULT 1,
        numberOfSpeakers INTEGER NOT NULL DEFAULT 1,
        dubbedAudioPath TEXT,
        dubbedAudioPathsJson TEXT NOT NULL DEFAULT '',
        dubbedVideoPathsJson TEXT NOT NULL DEFAULT '',
        autoDubStatusByLangJson TEXT NOT NULL DEFAULT '',
        autoDubJobIdByLangJson TEXT NOT NULL DEFAULT '',
        autoSubtitleStatus TEXT NOT NULL DEFAULT 'IDLE',
        autoDubStatus TEXT NOT NULL DEFAULT 'IDLE',
        autoSubtitleJobId TEXT,
        autoDubJobId TEXT,
        autoSubtitleError TEXT,
        autoDubError TEXT,
        separationJobId TEXT,
        separationSegmentId TEXT,
        separationNumberOfSpeakers INTEGER NOT NULL DEFAULT 2,
        separationMuteOriginal INTEGER NOT NULL DEFAULT 1,
        separationStatus TEXT NOT NULL DEFAULT 'IDLE',
        separationError TEXT,
        currentAudioRenderJobId TEXT,
        currentVideoRenderJobId TEXT,
        isRenderStale INTEGER NOT NULL DEFAULT 1
    )""",
    """INSERT INTO edit_projects_new (
        projectId, createdAt, updatedAt, title, pendingReviewTargetLangsCsv,
        frameWidth, frameHeight, backgroundColorHex,
        videoScale, videoOffsetXPct, videoOffsetYPct,
        targetLanguageCode, targetLanguageCodesJson,
        enableAutoDubbing, enableAutoSubtitles,
        showSubtitlesOnPreview, showDubbingOnPreview,
        numberOfSpeakers,
        dubbedAudioPath, dubbedAudioPathsJson, dubbedVideoPathsJson,
        autoDubStatusByLangJson, autoDubJobIdByLangJson,
        autoSubtitleStatus, autoDubStatus,
        autoSubtitleJobId, autoDubJobId, autoSubtitleError, autoDubError,
        separationJobId, separationSegmentId, separationNumberOfSpeakers,
        separationMuteOriginal, separationStatus, separationError,
        currentAudioRenderJobId, currentVideoRenderJobId, isRenderStale
    )
    SELECT
        projectId, createdAt, updatedAt, title, pendingReviewTargetLangsCsv,
        frameWidth, frameHeight, backgroundColorHex,
        videoScale, videoOffsetXPct, videoOffsetYPct,
        targetLanguageCode, targetLanguageCodesJson,
        enableAutoDubbing, enableAutoSubtitles,
        showSubtitlesOnPreview, showDubbingOnPreview,
        numberOfSpeakers,
        dubbedAudioPath, dubbedAudioPathsJson, dubbedVideoPathsJson,
        autoDubStatusByLangJson, autoDubJobIdByLangJson,
        autoSubtitleStatus, autoDubStatus,
        autoSubtitleJobId, autoDubJobId, autoSubtitleError, autoDubError,
        separationJobId, separationSegmentId, separationNumberOfSpeakers,
        separationMuteOriginal, separationStatus, separationError,
        NULL, currentRenderJobId, isRenderStale
    FROM edit_projects""",
    "DROP TABLE edit_projects",
    "ALTER TABLE edit_projects_new RENAME TO edit_projects"
)

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(connection: SQLiteConnection) {
        MIGRATION_31_32_STATEMENTS.forEach { connection.execSQL(it) }
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
    MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
    MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
)
