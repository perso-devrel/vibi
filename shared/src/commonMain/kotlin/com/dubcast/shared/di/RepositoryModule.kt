package com.dubcast.shared.di

import com.dubcast.shared.data.repository.AudioSeparationRepositoryImpl
import com.dubcast.shared.data.repository.BgmClipRepositoryImpl
import com.dubcast.shared.data.repository.DubClipRepositoryImpl
import com.dubcast.shared.data.repository.EditProjectRepositoryImpl
import com.dubcast.shared.data.repository.ImageClipRepositoryImpl
import com.dubcast.shared.data.repository.LanguageRepositoryImpl
import com.dubcast.shared.data.repository.RemoteRenderExecutor
import com.dubcast.shared.data.repository.SegmentRepositoryImpl
import com.dubcast.shared.data.repository.SubtitleClipRepositoryImpl
import com.dubcast.shared.data.repository.TextOverlayRepositoryImpl
import com.dubcast.shared.data.repository.TtsRepositoryImpl
import com.dubcast.shared.domain.repository.AudioSeparationRepository
import com.dubcast.shared.domain.repository.BgmClipRepository
import com.dubcast.shared.domain.repository.DubClipRepository
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.ImageClipRepository
import com.dubcast.shared.domain.repository.LanguageRepository
import com.dubcast.shared.domain.repository.SegmentRepository
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.domain.repository.TextOverlayRepository
import com.dubcast.shared.domain.repository.TtsRepository
import com.dubcast.shared.domain.usecase.export.FfmpegExecutor
import org.koin.dsl.module

val repositoryModule = module {
    single<EditProjectRepository> {
        EditProjectRepositoryImpl(
            database = get(),
            dao = get(),
            segmentDao = get(),
            dubClipDao = get(),
            subtitleClipDao = get(),
            imageClipDao = get(),
            textOverlayDao = get(),
            bgmClipDao = get(),
            separationDirectiveDao = get(),
        )
    }
    single<SegmentRepository> { SegmentRepositoryImpl(get()) }
    single<DubClipRepository> { DubClipRepositoryImpl(get()) }
    single<SubtitleClipRepository> { SubtitleClipRepositoryImpl(get()) }
    single<ImageClipRepository> { ImageClipRepositoryImpl(get()) }
    single<TextOverlayRepository> { TextOverlayRepositoryImpl(get()) }
    single<BgmClipRepository> { BgmClipRepositoryImpl(get()) }
    single<TtsRepository> {
        TtsRepositoryImpl(api = get(), httpClient = get(), bffBaseUrl = getProperty("bffBaseUrl"))
    }
    single<AudioSeparationRepository> { AudioSeparationRepositoryImpl(get()) }
    single<FfmpegExecutor> { RemoteRenderExecutor(api = get()) }
    single<LanguageRepository> { LanguageRepositoryImpl(api = get()) }
    single<com.dubcast.shared.domain.repository.SeparationDirectiveRepository> {
        com.dubcast.shared.data.repository.SeparationDirectiveRepositoryImpl(dao = get())
    }
    single { com.dubcast.shared.data.repository.ChatRepository(bffApi = get()) }
    single { com.dubcast.shared.domain.chat.ChatToolDispatcher() }
}
