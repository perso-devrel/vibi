package com.dubcast.shared.di

import com.dubcast.shared.domain.usecase.bgm.AddBgmClipUseCase
import com.dubcast.shared.domain.usecase.bgm.DeleteBgmClipUseCase
import com.dubcast.shared.domain.usecase.bgm.UpdateBgmClipUseCase
import com.dubcast.shared.domain.usecase.draft.ExpireOldDraftsUseCase
import com.dubcast.shared.domain.usecase.export.AssGenerator
import com.dubcast.shared.domain.usecase.export.ExportWithDubbingUseCase
import com.dubcast.shared.domain.usecase.image.AddImageClipUseCase
import com.dubcast.shared.domain.usecase.image.DeleteImageClipUseCase
import com.dubcast.shared.domain.usecase.image.UpdateImageClipUseCase
import com.dubcast.shared.domain.usecase.input.CreateProjectWithInitialVideoSegmentUseCase
import com.dubcast.shared.domain.usecase.input.SetProjectFrameUseCase
import com.dubcast.shared.domain.usecase.input.ValidateVideoUseCase
import com.dubcast.shared.domain.usecase.separation.PollSeparationUseCase
import com.dubcast.shared.domain.usecase.separation.StartAudioSeparationUseCase
import com.dubcast.shared.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.dubcast.shared.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.dubcast.shared.domain.usecase.subtitle.EditSubtitleClipUseCase
import com.dubcast.shared.domain.usecase.save.SaveAllVariantsUseCase
import com.dubcast.shared.domain.usecase.subtitle.GenerateAutoDubUseCase
import com.dubcast.shared.domain.usecase.subtitle.GenerateAutoSubtitlesUseCase
import com.dubcast.shared.domain.usecase.subtitle.GenerateOriginalScriptUseCase
import com.dubcast.shared.domain.usecase.subtitle.RegenerateSubtitlesUseCase
import com.dubcast.shared.domain.usecase.text.AddTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.DeleteTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.DuplicateTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.UpdateTextOverlayUseCase
import com.dubcast.shared.domain.usecase.timeline.AddImageSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.AddVideoSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.DeleteDubClipUseCase
import com.dubcast.shared.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.dubcast.shared.domain.usecase.timeline.MoveDubClipUseCase
import com.dubcast.shared.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.dubcast.shared.domain.usecase.timeline.RemoveSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.SplitSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import com.dubcast.shared.domain.usecase.tts.GetVoiceListUseCase
import com.dubcast.shared.domain.usecase.tts.SynthesizeDubClipUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val useCaseModule = module {
    // input
    factoryOf(::ValidateVideoUseCase)
    factoryOf(::CreateProjectWithInitialVideoSegmentUseCase)
    factoryOf(::SetProjectFrameUseCase)

    // draft (메인 화면 "이어서 작업" 만료 cleanup)
    factory { ExpireOldDraftsUseCase(get()) }

    // image
    factoryOf(::AddImageClipUseCase)
    factoryOf(::UpdateImageClipUseCase)
    factoryOf(::DeleteImageClipUseCase)

    // subtitle
    factoryOf(::AddSubtitleClipUseCase)
    factoryOf(::DeleteSubtitleClipUseCase)
    factoryOf(::EditSubtitleClipUseCase)
    factoryOf(::GenerateAutoSubtitlesUseCase)
    factoryOf(::GenerateAutoDubUseCase)
    factory { RegenerateSubtitlesUseCase(get(), get()) }
    factory { GenerateOriginalScriptUseCase(get(), get(), get()) }

    // save (timeline header → 모든 variant 렌더 + 갤러리 저장 — drafts 폐기 후 단일 저장 흐름)
    factory {
        SaveAllVariantsUseCase(
            platformAdapter = get(),
            gallerySaver = get(),
            editProjectRepository = get(),
            dubClipRepository = get(),
            subtitleClipRepository = get(),
            imageClipRepository = get(),
            segmentRepository = get(),
            textOverlayRepository = get(),
            bgmClipRepository = get(),
            separationDirectiveRepository = get(),
            bffApi = get(),
        )
    }

    // tts
    factoryOf(::GetVoiceListUseCase)
    factoryOf(::SynthesizeDubClipUseCase)

    // separation
    factoryOf(::StartAudioSeparationUseCase)
    factoryOf(::PollSeparationUseCase)

    // bgm
    factoryOf(::AddBgmClipUseCase)
    factoryOf(::UpdateBgmClipUseCase)
    factoryOf(::DeleteBgmClipUseCase)

    // text
    factoryOf(::AddTextOverlayUseCase)
    factoryOf(::UpdateTextOverlayUseCase)
    factoryOf(::DeleteTextOverlayUseCase)
    factoryOf(::DuplicateTextOverlayUseCase)

    // export
    singleOf(::AssGenerator)
    factoryOf(::ExportWithDubbingUseCase)

    // timeline
    factoryOf(::AddVideoSegmentUseCase)
    factoryOf(::AddImageSegmentUseCase)
    factoryOf(::RemoveSegmentUseCase)
    factoryOf(::RemoveSegmentRangeUseCase)
    factoryOf(::DuplicateSegmentRangeUseCase)
    factoryOf(::SplitSegmentUseCase)
    factoryOf(::MoveDubClipUseCase)
    factoryOf(::DeleteDubClipUseCase)
    factoryOf(::UpdateSegmentTrimUseCase)
    factoryOf(::UpdateSegmentVolumeUseCase)
    factoryOf(::UpdateSegmentSpeedUseCase)
    factoryOf(::UpdateImageSegmentDurationUseCase)
    factoryOf(::UpdateImageSegmentPositionUseCase)
}
