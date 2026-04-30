package com.dubcast.shared.di

import com.dubcast.shared.data.repository.AndroidAudioMetadataExtractor
import com.dubcast.shared.data.repository.AndroidGallerySaver
import com.dubcast.shared.data.repository.AndroidImageMetadataExtractor
import com.dubcast.shared.data.repository.AndroidVideoMetadataExtractor
import com.dubcast.shared.data.repository.AutoDubRepositoryImpl
import com.dubcast.shared.data.repository.AutoSubtitleRepositoryImpl
import com.dubcast.shared.data.repository.MediaJobUploader
import com.dubcast.shared.domain.repository.AutoDubRepository
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import com.dubcast.shared.domain.usecase.input.ImageMetadataExtractor
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.ui.export.AndroidExportPlatformAdapter
import com.dubcast.shared.ui.export.ExportPlatformAdapter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    single<ExportPlatformAdapter> {
        AndroidExportPlatformAdapter(
            context = androidContext(),
            exportWithDubbing = get()
        )
    }
    single<VideoMetadataExtractor> { AndroidVideoMetadataExtractor(androidContext()) }
    single<AudioMetadataExtractor> { AndroidAudioMetadataExtractor(androidContext()) }
    single<ImageMetadataExtractor> { AndroidImageMetadataExtractor(androidContext()) }
    single<GallerySaver> { AndroidGallerySaver(androidContext()) }

    single { MediaJobUploader(androidContext()) }
    single<AutoDubRepository> { AutoDubRepositoryImpl(api = get(), uploader = get(), context = androidContext()) }
    single<AutoSubtitleRepository> { AutoSubtitleRepositoryImpl(api = get(), uploader = get()) }
}
