package com.vibi.shared.di

import com.vibi.shared.data.local.AuthTokenStore
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.repository.AudioSeparationRepositoryImpl
import com.vibi.shared.data.repository.AuthRepository
import com.vibi.shared.data.repository.BgmClipRepositoryImpl
import com.vibi.shared.data.repository.EditProjectRepositoryImpl
import com.vibi.shared.data.repository.ImageClipRepositoryImpl
import com.vibi.shared.data.repository.RemoteRenderExecutor
import com.vibi.shared.data.repository.SegmentRepositoryImpl
import com.vibi.shared.data.repository.TextOverlayRepositoryImpl
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.ImageClipRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.TextOverlayRepository
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import org.koin.dsl.module

val repositoryModule = module {
    // 계정별 로컬 데이터 분리 — Repository / AuthRepository 가 공유.
    single { UserSession() }

    single<EditProjectRepository> {
        EditProjectRepositoryImpl(
            database = get(),
            dao = get(),
            segmentDao = get(),
            imageClipDao = get(),
            textOverlayDao = get(),
            bgmClipDao = get(),
            separationDirectiveDao = get(),
            userSession = get(),
        )
    }
    single<SegmentRepository> { SegmentRepositoryImpl(get()) }
    single<ImageClipRepository> { ImageClipRepositoryImpl(get()) }
    single<TextOverlayRepository> { TextOverlayRepositoryImpl(get()) }
    single<BgmClipRepository> { BgmClipRepositoryImpl(get()) }
    single<AudioSeparationRepository> {
        AudioSeparationRepositoryImpl(get(), getProperty<String>("bffBaseUrl"))
    }
    single { RemoteRenderExecutor(api = get()) }
    single<FfmpegExecutor> { get<RemoteRenderExecutor>() }
    single<com.vibi.shared.domain.repository.SeparationDirectiveRepository> {
        com.vibi.shared.data.repository.SeparationDirectiveRepositoryImpl(dao = get())
    }

    // 인증 — Settings / GoogleSignInClient 는 platform 모듈에서 주입.
    single { AuthTokenStore(settings = get()) }
    single { com.vibi.shared.data.local.CreditStore(settings = get(), userSession = get()) }
    single {
        com.vibi.shared.data.repository.CreditPurchaseService(
            bffApi = get(),
            tokenStore = get(),
            userSession = get(),
            creditStore = get(),
        )
    }
    single {
        AuthRepository(
            googleSignInClient = get(),
            appleSignInClient = get(),
            bffApi = get(),
            tokenStore = get(),
            userSession = get(),
        )
    }
}
