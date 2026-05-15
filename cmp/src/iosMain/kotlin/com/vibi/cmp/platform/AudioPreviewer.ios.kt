package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * 미리듣기 player.
 *
 * Local file → AVAudioPlayer (synchronous prepareToPlay, deterministic).
 * Remote URL → background 다운로드 후 AVAudioPlayer (K/N AVPlayer streaming silent fail 우회).
 *
 * 자연 종료 감지: AVAudioPlayer delegate (audioPlayerDidFinishPlaying) 가 onComplete 호출.
 * stop() 은 delegate 를 먼저 떼서 finish 콜백 발화 막음.
 *
 * 구현 형태가 [IosAudioPreviewerHandle] 클래스로 분리된 이유: Composable 안의 익명 object
 * 안에 nested local function (예전 `startWithFile`) 을 두고 mutable state 를 캡처하면 K/N
 * LocalDeclarationsLowering 가 깨짐 (`No dispatch receiver parameter for FUN LOCAL_FUNCTION`).
 * StemMixer.ios.kt 와 동일하게 별도 class 로 분리하면 회피된다.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val scope = rememberCoroutineScope()
    val handle = remember(scope) { IosAudioPreviewerHandle(scope) }
    DisposableEffect(handle) {
        onDispose { handle.dispose() }
    }
    return handle
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosAudioPreviewerHandle(
    private val scope: CoroutineScope,
) : AudioPreviewerHandle {
    private val progress: MutableLongState = mutableLongStateOf(0L)
    private val duration: MutableLongState = mutableLongStateOf(0L)
    override val progressMs: State<Long> get() = progress
    override val durationMs: State<Long> get() = duration

    private var delegateRef: NSObject? = null
    private var localPlayer: AVAudioPlayer? = null
    /** 마지막으로 만든 임시 파일 path (remote download 결과). 새 play 또는 dispose 시 삭제. */
    private var lastTempPath: String? = null
    /** 위치 polling job — play 마다 새로 시작, stop/finish 시 cancel. */
    private var pollJob: Job? = null

    fun dispose() {
        cancelPoll()
        localPlayer?.let {
            it.delegate = null
            it.stop()
        }
        deleteLastTemp()
    }

    private fun deleteLastTemp() {
        lastTempPath?.let { deleteCachedAudio(it) }
        lastTempPath = null
    }

    private fun cancelPoll() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun play(url: String, volume: Float, rate: Float, onComplete: () -> Unit) {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        }
        val clampedVol = volume.coerceIn(0f, 1f)
        val clampedRate = rate.coerceIn(0.5f, 2.0f)

        val absoluteUrl = resolveAbsoluteAudioUrl(url)
        val isRemote = absoluteUrl.startsWith("http://") || absoluteUrl.startsWith("https://")

        if (!isRemote) {
            val fileUrl = resolveStoredUriToFileUrl(absoluteUrl) ?: run {
                println("[AudioPreviewer] cannot resolve local: $absoluteUrl")
                return
            }
            startWithFile(fileUrl, clampedVol, clampedRate, absoluteUrl, onComplete)
            return
        }

        scope.launch {
            val tempPath = downloadAudioToCache(absoluteUrl, prefix = "preview") ?: run {
                println("[AudioPreviewer] download failed: $absoluteUrl")
                return@launch
            }
            // 새 임시 파일로 교체 — 이전 파일 삭제.
            deleteLastTemp()
            lastTempPath = tempPath
            startWithFile(NSURL.fileURLWithPath(tempPath), clampedVol, clampedRate, absoluteUrl, onComplete)
        }
    }

    private fun startWithFile(
        fileUrl: NSURL,
        clampedVol: Float,
        clampedRate: Float,
        absoluteUrl: String,
        onComplete: () -> Unit,
    ) {
        val player = runCatching {
            AVAudioPlayer(contentsOfURL = fileUrl, error = null)
        }.getOrNull() ?: run {
            println("[AudioPreviewer] AVAudioPlayer init failed: $absoluteUrl")
            return
        }
        cancelPoll()
        localPlayer?.let {
            it.delegate = null
            it.stop()
        }
        player.enableRate = true
        player.volume = clampedVol
        player.rate = clampedRate
        val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
            override fun audioPlayerDidFinishPlaying(
                player: AVAudioPlayer,
                successfully: Boolean,
            ) {
                cancelPoll()
                progress.longValue = 0L
                onComplete()
            }
        }
        delegateRef = delegate
        player.delegate = delegate
        player.prepareToPlay()
        duration.longValue = (player.duration * 1000.0).toLong().coerceAtLeast(0L)
        progress.longValue = 0L
        player.play()
        localPlayer = player
        // poll currentTime — UI playhead 갱신용. 50ms 해상도면 60fps 슬라이더 따라가기 충분.
        // currentTime 이 같은 ms 로 truncate 되는 tick 은 write 스킵 — MutableLongState 는 항상
        // 동일값에도 알림이 안 가지만, 명시적 가드로 의도가 명확하고 향후 State 종류 바꿔도 안전.
        pollJob = scope.launch {
            var lastWritten = -1L
            while (isActive) {
                val p = localPlayer ?: break
                val now = (p.currentTime * 1000.0).toLong().coerceAtLeast(0L)
                if (now != lastWritten) {
                    progress.longValue = now
                    lastWritten = now
                }
                if (!p.playing) break
                delay(50)
            }
        }
    }

    override fun stop() {
        cancelPoll()
        localPlayer?.let {
            it.delegate = null
            it.stop()
        }
        localPlayer = null
        delegateRef = null
        deleteLastTemp()
        progress.longValue = 0L
        duration.longValue = 0L
    }

    override fun seekTo(ms: Long) {
        val p = localPlayer ?: return
        val total = duration.longValue
        val target = if (total > 0L) ms.coerceIn(0L, total) else ms.coerceAtLeast(0L)
        p.currentTime = target / 1000.0
        progress.longValue = target
    }
}
