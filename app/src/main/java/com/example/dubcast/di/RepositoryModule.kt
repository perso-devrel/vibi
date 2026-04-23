package com.example.dubcast.di

import com.example.dubcast.data.repository.AndroidAudioMetadataExtractor
import com.example.dubcast.data.repository.AndroidGallerySaver
import com.example.dubcast.data.repository.AndroidImageMetadataExtractor
import com.example.dubcast.data.repository.AndroidVideoMetadataExtractor
import com.example.dubcast.data.repository.AudioSeparationRepositoryImpl
import com.example.dubcast.data.repository.BgmClipRepositoryImpl
import com.example.dubcast.data.repository.DubClipRepositoryImpl
import com.example.dubcast.data.repository.EditProjectRepositoryImpl
import com.example.dubcast.data.repository.ImageClipRepositoryImpl
import com.example.dubcast.data.repository.RemoteRenderExecutor
import com.example.dubcast.data.repository.SegmentRepositoryImpl
import com.example.dubcast.data.repository.SubtitleClipRepositoryImpl
import com.example.dubcast.data.repository.TextOverlayRepositoryImpl
import com.example.dubcast.data.repository.TtsRepositoryImpl
import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.BgmClipRepository
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.SegmentRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TextOverlayRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import com.example.dubcast.domain.usecase.input.AudioMetadataExtractor
import com.example.dubcast.domain.usecase.input.ImageMetadataExtractor
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor
import com.example.dubcast.domain.usecase.share.GallerySaver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoMetadataExtractor(impl: AndroidVideoMetadataExtractor): VideoMetadataExtractor

    @Binds
    @Singleton
    abstract fun bindImageMetadataExtractor(impl: AndroidImageMetadataExtractor): ImageMetadataExtractor

    @Binds
    @Singleton
    abstract fun bindAudioMetadataExtractor(impl: AndroidAudioMetadataExtractor): AudioMetadataExtractor

    @Binds
    @Singleton
    abstract fun bindFfmpegExecutor(impl: RemoteRenderExecutor): FfmpegExecutor

    @Binds
    @Singleton
    abstract fun bindGallerySaver(impl: AndroidGallerySaver): GallerySaver

    @Binds
    @Singleton
    abstract fun bindDubClipRepository(impl: DubClipRepositoryImpl): DubClipRepository

    @Binds
    @Singleton
    abstract fun bindEditProjectRepository(impl: EditProjectRepositoryImpl): EditProjectRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleClipRepository(impl: SubtitleClipRepositoryImpl): SubtitleClipRepository

    @Binds
    @Singleton
    abstract fun bindTtsRepository(impl: TtsRepositoryImpl): TtsRepository

    @Binds
    @Singleton
    abstract fun bindImageClipRepository(impl: ImageClipRepositoryImpl): ImageClipRepository

    @Binds
    @Singleton
    abstract fun bindSegmentRepository(impl: SegmentRepositoryImpl): SegmentRepository

    @Binds
    @Singleton
    abstract fun bindTextOverlayRepository(impl: TextOverlayRepositoryImpl): TextOverlayRepository

    @Binds
    @Singleton
    abstract fun bindBgmClipRepository(impl: BgmClipRepositoryImpl): BgmClipRepository

    @Binds
    @Singleton
    abstract fun bindAudioSeparationRepository(impl: AudioSeparationRepositoryImpl): AudioSeparationRepository
}
