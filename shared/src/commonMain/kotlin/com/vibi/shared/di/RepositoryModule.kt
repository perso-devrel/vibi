package com.vibi.shared.di

import com.vibi.shared.data.local.AuthTokenStore
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.repository.AudioSeparationRepositoryImpl
import com.vibi.shared.data.repository.AuthRepository
import com.vibi.shared.data.repository.BgmClipRepositoryImpl
import com.vibi.shared.data.repository.EditProjectRepositoryImpl
import com.vibi.shared.data.remote.AssetKeyCache
import com.vibi.shared.data.remote.AssetUploadManager
import com.vibi.shared.data.repository.RemoteRenderExecutor
import com.vibi.shared.data.repository.SegmentRepositoryImpl
import com.vibi.shared.data.repository.TextOverlayRepositoryImpl
import com.vibi.shared.data.repository.V3RenderExecutor
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.TextOverlayRepository
import org.koin.dsl.module

val repositoryModule = module {
    // 계정별 로컬 데이터 분리 — Repository / AuthRepository 가 공유.
    single { UserSession() }

    single<EditProjectRepository> {
        EditProjectRepositoryImpl(
            database = get(),
            dao = get(),
            segmentDao = get(),
            textOverlayDao = get(),
            bgmClipDao = get(),
            separationDirectiveDao = get(),
            userSession = get(),
        )
    }
    single<SegmentRepository> { SegmentRepositoryImpl(get()) }
    single<TextOverlayRepository> { TextOverlayRepositoryImpl(get()) }
    single<BgmClipRepository> { BgmClipRepositoryImpl(get()) }
    single<AudioSeparationRepository> {
        AudioSeparationRepositoryImpl(
            api = get(),
            bffBaseUrl = getProperty<String>("bffBaseUrl"),
            audioExtractor = get(),
            creditStore = get(),
            userSession = get(),
        )
    }
    single { RemoteRenderExecutor(api = get()) }
    // v3 (asset-by-reference) — iOS 전용 흐름. settings 는 platform 모듈이 주입.
    single { AssetKeyCache(settings = get()) }
    single { AssetUploadManager(api = get(), cache = get()) }
    single { V3RenderExecutor(api = get(), assetUploader = get()) }
    // FfmpegExecutor 바인딩은 platform 별 module 에서 결정:
    //   iOS    → V3RenderExecutor (v3 asset-by-reference)
    //   Android → RemoteRenderExecutor (v2 multipart, sha256/statFile 미구현 회피)
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
