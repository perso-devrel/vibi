package com.vibi.shared.platform

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Google Mobile Ads (AdMob) 보상형 광고 — iOS [IosRewardedAdController] + Swift `RewardedAdBridgeImpl`
 * 와 동등한 역할을 Android 에서 SDK 직접 호출로 수행한다 (`AndroidIapClient` 와 같은 패턴).
 *
 * 매 호출마다 광고를 1회 load → show. 보상 적립은 클라가 하지 않는다 — [ServerSideVerificationOptions]
 * 에 [showRewardedAd] 의 userId 를 실어두면 시청 완료 시 Google 이 BFF `/credits/admob-ssv` 로
 * 서명 콜백을 보내 +1 크레딧을 지급한다. 본 컨트롤러는 "끝까지 봤는지"만 반환.
 *
 * SDK 호출(load/show)은 메인 스레드에서 해야 하므로 [Dispatchers.Main] 으로 전환.
 */
class AndroidRewardedAdController(
    private val appContext: Context,
    private val activityProvider: ActivityProvider,
) : RewardedAdController {

    override suspend fun showRewardedAd(userId: String): RewardedAdOutcome =
        withContext(Dispatchers.Main) {
            val activity = activityProvider.current ?: return@withContext RewardedAdOutcome.UNAVAILABLE
            val ad = loadAd() ?: return@withContext RewardedAdOutcome.UNAVAILABLE
            showLoaded(ad, activity, userId)
        }

    private suspend fun loadAd(): RewardedAd? = suspendCancellableCoroutine { cont ->
        // 비맞춤(non-personalized) 광고 요청 — npa=1. 앱 간 추적 없이 맥락 기반으로만 광고 노출
        // → ATT/추적 매니페스트 불필요. 맞춤 전환 시 이 extras 를 빼고 동의(UMP) 흐름을 추가.
        val npaExtras = Bundle().apply { putString("npa", "1") }
        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, npaExtras)
            .build()
        RewardedAd.load(
            appContext,
            REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    private suspend fun showLoaded(
        ad: RewardedAd,
        activity: Activity,
        userId: String,
    ): RewardedAdOutcome = suspendCancellableCoroutine { cont ->
        var earned = false
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder().setUserId(userId).build()
        )
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (cont.isActive) {
                    cont.resume(
                        if (earned) RewardedAdOutcome.REWARD_EARNED else RewardedAdOutcome.DISMISSED
                    )
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                if (cont.isActive) cont.resume(RewardedAdOutcome.UNAVAILABLE)
            }
        }
        ad.show(activity) { earned = true }
    }

    private companion object {
        // AdMob Android 보상형 광고 단위 ID (프로덕션).
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-4825847811436125/4262203856"
    }
}
