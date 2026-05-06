package com.dubcast.shared.domain.usecase.save

import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import kotlinx.coroutines.flow.first

/**
 * 저장/공유 picker sheet 가 노출할 variant 목록을 계산.
 *
 * 키 정의·순서는 [SaveAllVariantsUseCase.computeAllVariantKeys] 와 동일하게 단일 source 사용.
 *
 * displayLabel 매핑:
 *  - ORIGINAL → "원본 영상"
 *  - ORIGINAL_SUBTITLE → "원본 자막"
 *  - TRANSLATION_DUB (자막 동시 존재) → "{LANG} 더빙+자막"
 *  - TRANSLATION_DUB (자막 없음) → "{LANG} 더빙"
 *  - TRANSLATION_SUBTITLE → "{LANG} 자막"
 *
 * 더빙 + 같은 lang 자막을 동시 burn 하는 케이스는 라벨만 분기 — kind 는 그대로
 * [ExportVariantKind.TRANSLATION_DUB] 유지 (다운스트림 분기 단일성 + 사용자 설명력 양립).
 */
class ListExportVariantsUseCase(
    private val editProjectRepository: EditProjectRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
) {

    suspend operator fun invoke(projectId: String): Result<List<ExportVariant>> = runCatching {
        val project = editProjectRepository.getProject(projectId)
            ?: error("Project not found: $projectId")
        val allSubtitleClips = subtitleClipRepository.observeClips(projectId).first()
        val langsWithSubtitle = SaveAllVariantsUseCase.collectLangsWithSubtitle(allSubtitleClips)
        val langsWithDub = SaveAllVariantsUseCase.collectLangsWithDub(project)

        val keys = SaveAllVariantsUseCase.computeAllVariantKeys(
            project = project,
            allSubtitleClips = allSubtitleClips,
        )

        keys.map { key ->
            when {
                key == ExportVariant.KEY_ORIGINAL -> ExportVariant(
                    key = key,
                    kind = ExportVariantKind.ORIGINAL,
                    displayLabel = "원본 영상",
                )
                key == ExportVariant.KEY_ORIGINAL_SUBTITLE -> ExportVariant(
                    key = key,
                    kind = ExportVariantKind.ORIGINAL_SUBTITLE,
                    displayLabel = "원본 자막",
                )
                key in langsWithDub -> {
                    val hasSubtitleForLang = key in langsWithSubtitle
                    val label = if (hasSubtitleForLang) {
                        "${key.uppercase()} 더빙+자막"
                    } else {
                        "${key.uppercase()} 더빙"
                    }
                    ExportVariant(
                        key = key,
                        kind = ExportVariantKind.TRANSLATION_DUB,
                        displayLabel = label,
                    )
                }
                else -> ExportVariant(
                    key = key,
                    kind = ExportVariantKind.TRANSLATION_SUBTITLE,
                    displayLabel = "${key.uppercase()} 자막",
                )
            }
        }
    }
}
