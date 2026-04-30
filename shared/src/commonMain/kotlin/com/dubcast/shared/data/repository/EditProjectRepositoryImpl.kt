package com.dubcast.shared.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.dubcast.shared.data.local.db.DubCastDatabase
import com.dubcast.shared.data.local.db.dao.BgmClipDao
import com.dubcast.shared.data.local.db.dao.DubClipDao
import com.dubcast.shared.data.local.db.dao.EditProjectDao
import com.dubcast.shared.data.local.db.dao.ImageClipDao
import com.dubcast.shared.data.local.db.dao.SegmentDao
import com.dubcast.shared.data.local.db.dao.SeparationDirectiveDao
import com.dubcast.shared.data.local.db.dao.SubtitleClipDao
import com.dubcast.shared.data.local.db.dao.TextOverlayDao
import com.dubcast.shared.data.local.db.entity.EditProjectEntity
import com.dubcast.shared.data.local.db.entity.SegmentEntity
import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.repository.EditProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EditProjectRepositoryImpl constructor(
    private val database: DubCastDatabase,
    private val dao: EditProjectDao,
    private val segmentDao: SegmentDao,
    private val dubClipDao: DubClipDao,
    private val subtitleClipDao: SubtitleClipDao,
    private val imageClipDao: ImageClipDao,
    private val textOverlayDao: TextOverlayDao,
    private val bgmClipDao: BgmClipDao,
    private val separationDirectiveDao: SeparationDirectiveDao,
) : EditProjectRepository {

    override suspend fun createProject(project: EditProject) {
        dao.insert(project.toEntity())
    }

    override suspend fun createProjectWithSegment(project: EditProject, segment: Segment) {
        require(segment.projectId == project.projectId) {
            "Segment.projectId must match EditProject.projectId"
        }
        database.useWriterConnection { conn ->
            conn.immediateTransaction {
                dao.insert(project.toEntity())
                segmentDao.insert(segment.toEntity())
            }
        }
    }

    override fun observeProject(projectId: String): Flow<EditProject?> =
        dao.observeById(projectId).map { it?.toDomain() }

    override suspend fun getProject(projectId: String): EditProject? {
        return dao.getById(projectId)?.toDomain()
    }

    override suspend fun updateProject(project: EditProject) {
        dao.update(project.toEntity())
    }

    override suspend fun deleteProject(projectId: String) {
        cascadeDeleteProject(projectId)
    }

    override fun observeAllProjects(): Flow<List<EditProject>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun expireOldDrafts(thresholdMs: Long) {
        // updatedAt < threshold 인 projectId 들 fetch 후 각각 cascade.
        // 자식 cascade 삭제도 runCatching 로 감싸 일부 실패 시 다음 project 진행.
        val expiredIds = runCatching { dao.getExpiredIds(thresholdMs) }.getOrDefault(emptyList())
        expiredIds.forEach { id ->
            runCatching { cascadeDeleteProject(id) }
        }
    }

    private suspend fun cascadeDeleteProject(projectId: String) {
        // 자식 row 들 — Room FK ON DELETE CASCADE 미설정 환경에서 명시 cleanup.
        runCatching { segmentDao.deleteByProjectId(projectId) }
        runCatching { dubClipDao.deleteByProjectId(projectId) }
        runCatching { subtitleClipDao.deleteByProjectId(projectId) }
        runCatching { imageClipDao.deleteByProjectId(projectId) }
        runCatching { textOverlayDao.deleteByProjectId(projectId) }
        runCatching { bgmClipDao.deleteByProjectId(projectId) }
        runCatching { separationDirectiveDao.deleteByProject(projectId) }
        dao.deleteById(projectId)
    }

    private fun EditProjectEntity.toDomain() = EditProject(
        projectId = projectId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        pendingReviewTargetLangsCsv = pendingReviewTargetLangsCsv,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct,
        targetLanguageCode = targetLanguageCode,
        targetLanguageCodes = decodeLanguageCodes(targetLanguageCodesJson),
        enableAutoDubbing = enableAutoDubbing,
        enableAutoSubtitles = enableAutoSubtitles,
        showSubtitlesOnPreview = showSubtitlesOnPreview,
        showDubbingOnPreview = showDubbingOnPreview,
        numberOfSpeakers = numberOfSpeakers,
        dubbedAudioPath = dubbedAudioPath,
        dubbedAudioPaths = decodeStringMap(dubbedAudioPathsJson),
        dubbedVideoPaths = decodeStringMap(dubbedVideoPathsJson),
        autoDubStatusByLang = decodeStringMap(autoDubStatusByLangJson)
            .mapValues { (_, v) -> parseStatus(v) },
        autoDubJobIdByLang = decodeStringMap(autoDubJobIdByLangJson),
        autoSubtitleStatus = parseStatus(autoSubtitleStatus),
        autoDubStatus = parseStatus(autoDubStatus),
        autoSubtitleJobId = autoSubtitleJobId,
        autoDubJobId = autoDubJobId,
        autoSubtitleError = autoSubtitleError,
        autoDubError = autoDubError,
        separationJobId = separationJobId,
        separationSegmentId = separationSegmentId,
        separationNumberOfSpeakers = separationNumberOfSpeakers,
        separationMuteOriginal = separationMuteOriginal,
        separationStatus = parseStatus(separationStatus),
        separationError = separationError,
    )

    private fun EditProject.toEntity() = EditProjectEntity(
        projectId = projectId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        pendingReviewTargetLangsCsv = pendingReviewTargetLangsCsv,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct,
        targetLanguageCode = targetLanguageCode,
        targetLanguageCodesJson = encodeLanguageCodes(targetLanguageCodes),
        enableAutoDubbing = enableAutoDubbing,
        enableAutoSubtitles = enableAutoSubtitles,
        showSubtitlesOnPreview = showSubtitlesOnPreview,
        showDubbingOnPreview = showDubbingOnPreview,
        numberOfSpeakers = numberOfSpeakers,
        dubbedAudioPath = dubbedAudioPath,
        dubbedAudioPathsJson = encodeStringMap(dubbedAudioPaths),
        dubbedVideoPathsJson = encodeStringMap(dubbedVideoPaths),
        autoDubStatusByLangJson = encodeStringMap(autoDubStatusByLang.mapValues { it.value.name }),
        autoDubJobIdByLangJson = encodeStringMap(autoDubJobIdByLang),
        autoSubtitleStatus = autoSubtitleStatus.name,
        autoDubStatus = autoDubStatus.name,
        autoSubtitleJobId = autoSubtitleJobId,
        autoDubJobId = autoDubJobId,
        autoSubtitleError = autoSubtitleError,
        autoDubError = autoDubError,
        separationJobId = separationJobId,
        separationSegmentId = separationSegmentId,
        separationNumberOfSpeakers = separationNumberOfSpeakers,
        separationMuteOriginal = separationMuteOriginal,
        separationStatus = separationStatus.name,
        separationError = separationError,
    )

    /** {key:value, ...} 단순 직렬화. value 에 콤마/콜론/따옴표 없는 경로·jobId 가정. */
    private fun encodeStringMap(map: Map<String, String>): String {
        if (map.isEmpty()) return ""
        return map.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"${escape(k)}\":\"${escape(v)}\""
        }
    }

    private fun decodeStringMap(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        val body = json.trim().removePrefix("{").removeSuffix("}")
        if (body.isBlank()) return emptyMap()
        return body.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) null else {
                    val k = unescape(parts[0].trim().removeSurrounding("\""))
                    val v = unescape(parts[1].trim().removeSurrounding("\""))
                    if (k.isEmpty()) null else k to v
                }
            }
            .toMap()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")

    /** JSON array of strings 직렬화. 빈 리스트면 빈 문자열로 저장 (구 데이터와 동등). */
    private fun encodeLanguageCodes(codes: List<String>): String {
        if (codes.isEmpty()) return ""
        return codes.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.replace("\"", "\\\"")}\"" }
    }

    private fun decodeLanguageCodes(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        // 단순 파서 — kotlinx.serialization Json 의존을 굳이 매퍼에 끌어오지 않기 위함.
        return json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"") }
            .filter { it.isNotEmpty() }
    }

    private fun parseStatus(name: String): AutoJobStatus =
        runCatching { AutoJobStatus.valueOf(name) }.getOrDefault(AutoJobStatus.IDLE)

    private fun Segment.toEntity() = SegmentEntity(
        id = id,
        projectId = projectId,
        type = type.name,
        order = order,
        sourceUri = sourceUri,
        durationMs = durationMs,
        width = width,
        height = height,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        imageXPct = imageXPct,
        imageYPct = imageYPct,
        imageWidthPct = imageWidthPct,
        imageHeightPct = imageHeightPct
    )
}
