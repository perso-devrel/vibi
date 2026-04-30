package com.dubcast.shared.di

import com.dubcast.shared.data.repository.IosAudioMetadataExtractor
import com.dubcast.shared.data.repository.IosAutoDubRepositoryImpl
import com.dubcast.shared.data.repository.IosAutoSubtitleRepositoryImpl
import com.dubcast.shared.data.repository.IosGallerySaver
import com.dubcast.shared.data.repository.IosImageMetadataExtractor
import com.dubcast.shared.data.repository.IosMediaJobUploader
import com.dubcast.shared.data.repository.IosVideoMetadataExtractor
import com.dubcast.shared.domain.repository.AutoDubRepository
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import com.dubcast.shared.domain.usecase.input.ImageMetadataExtractor
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.ui.export.ExportPlatformAdapter
import com.dubcast.shared.ui.export.IosExportPlatformAdapter
import org.koin.dsl.module

/**
 * iOS 측 platform module.
 */
val iosPlatformModule = module {
    single<ExportPlatformAdapter> { IosExportPlatformAdapter(exportWithDubbing = get()) }
    single<GallerySaver> { IosGallerySaver() }
    single<VideoMetadataExtractor> { IosVideoMetadataExtractor() }
    single<AudioMetadataExtractor> { IosAudioMetadataExtractor() }
    single<ImageMetadataExtractor> { IosImageMetadataExtractor() }
    single { IosMediaJobUploader() }
    single<AutoDubRepository> { IosAutoDubRepositoryImpl(api = get(), uploader = get()) }
    single<AutoSubtitleRepository> { IosAutoSubtitleRepositoryImpl(api = get(), uploader = get()) }
}
