package com.example.dubcast.di

import com.example.dubcast.data.repository.AndroidGallerySaver
import com.example.dubcast.data.repository.AndroidVideoMetadataExtractor
import com.example.dubcast.data.repository.DubClipRepositoryImpl
import com.example.dubcast.data.repository.EditProjectRepositoryImpl
import com.example.dubcast.data.repository.RemoteRenderExecutor
import com.example.dubcast.data.repository.LipSyncRepositoryImpl
import com.example.dubcast.data.repository.SubtitleClipRepositoryImpl
import com.example.dubcast.data.repository.TtsRepositoryImpl
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.LipSyncRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
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
    abstract fun bindLipSyncRepository(impl: LipSyncRepositoryImpl): LipSyncRepository
}
