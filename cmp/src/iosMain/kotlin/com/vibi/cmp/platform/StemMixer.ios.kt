package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFrameCount
import platform.AVFAudio.AVAudioFramePosition
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioUnitEQ
import platform.AVFAudio.AVAudioUnitTimePitch
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol
import kotlin.math.log10

/**
 * iOS: AVAudioEngine 기반 mixer. stem 마다 PlayerNode → TimePitch → EQ → mainMixer chain.
 *
 *  - 0..1 볼륨: PlayerNode.volume (linear)
 *  - 1..2 부스트: EQ.globalGain (dB) — AVAudioPlayer 시절 silent no-op 이던 영역. render path
 *    (ffmpeg volume filter 0..2) 와 동일 amplitude scaling 으로 preview-render 대칭.
 *  - 속도: TimePitch.rate (pitch-corrected stretch) — 영상 segment.speedScale 와 sync.
 *
 * directive group 별 chain 사전 attach + active group 만 play. directive 전환 시 다운로드/init 끊김 없음.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberStemMixer(): StemMixerHandle {
    val scope = rememberCoroutineScope()
    val handle = remember(scope) { IosStemMixerHandle(scope) }
    DisposableEffect(handle) {
        onDispose { handle.release() }
    }
    return handle
}

@OptIn(ExperimentalForeignApi::class)
private class IosStemMixerHandle(
    private val scope: CoroutineScope,
) : StemMixerHandle {

    /** 모든 stem 이 공유하는 단일 engine. mainMixerNode 가 자동으로 multi-input format 변환. */
    private val engine = AVAudioEngine()
    private var engineStarted = false

    private class StemNode(
        val playerNode: AVAudioPlayerNode,
        val timePitch: AVAudioUnitTimePitch,
        val eq: AVAudioUnitEQ,
        val file: AVAudioFile,
    )

    /** key = "groupId/stemId" — 같은 stemId 라도 다른 group 이면 별도 node chain. */
    private val nodes = mutableMapOf<String, StemNode>()
    /** key → groupId. */
    private val groupOfNode = mutableMapOf<String, String>()
    /** stemId → 적용된 마지막 volume (0..2). 새 node 생성 시 적용. */
    private val pendingVolumes = mutableMapOf<String, Float>()
    /** groupId → 마지막 seek 위치(ms). 새 node 가 download 후 올바른 offset 에서 시작. */
    private val pendingSeekByGroup = mutableMapOf<String, Long>()
    /** 적용된 마지막 rate. 새 node 도 동일 속도로 시작. 1.0 = 원본. */
    private var pendingRate: Float = 1f
    /** 이미 schedule 된 key — 신규 schedule 필요 여부 판단. */
    private val scheduledKeys = mutableSetOf<String>()
    private var activeGroupId: String? = null
    private var playing = false
    private var loadGen = 0

    /** group 별 "현재 재생 epoch" 추적 — seekTo drift 가드. */
    private val epochOriginMsByGroup = mutableMapOf<String, Long>()
    private val epochClockMsByGroup = mutableMapOf<String, Long>()

    /** AVAudioSession.setActive(true) 가 호출됐는지. load 시 즉시 활성화하면 영상 AVPlayer init 와
     *  같은 cycle 에 hardware audio unit 경합 → IOWorkLoop overload. play() 시점에 lazy activate. */
    private var sessionActivated = false

    private val interruptionObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter
        .addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = null,
            usingBlock = { notification ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey)
                    as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) pause()
            },
        )
    private val routeChangeObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter
        .addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = null,
            usingBlock = { notification ->
                val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey)
                    as? NSNumber)?.unsignedLongValue
                if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) pause()
            },
        )

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    /** key → audioUrl. 같은 key 라도 url 이 변하면 (token refresh 등) 재다운로드. */
    private val urlByKey = mutableMapOf<String, String>()
    private val tempPathByKey = mutableMapOf<String, String>()

    private fun deleteTemp(key: String) {
        tempPathByKey.remove(key)?.let { deleteCachedAudio(it) }
    }

    /** 0..1 → playerNode.volume, 1..2 → eq.globalGain dB. 동시에 둘 다 갱신해 일관성 유지. */
    private fun applyVolume(node: StemNode, v: Float) {
        val clamped = v.coerceIn(0f, 2f)
        node.playerNode.volume = clamped.coerceAtMost(1f)
        // log10(0) = -∞ 회피 + clamped <= 1 일 땐 EQ 통과 (0 dB).
        node.eq.globalGain = if (clamped > 1f) (20.0 * log10(clamped.toDouble())).toFloat() else 0f
    }

    private fun nowMs(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()

    /** group 의 "이 시각 현재 재생 추정 위치(ms)". seekTo drift 가드용. */
    private fun estimatedPositionMs(groupId: String): Long {
        val origin = epochOriginMsByGroup[groupId] ?: return pendingSeekByGroup[groupId] ?: 0L
        val clock = epochClockMsByGroup[groupId] ?: return origin
        return if (playing) {
            origin + ((nowMs() - clock) * pendingRate).toLong()
        } else {
            origin
        }
    }

    private fun cycleEpoch(groupId: String, positionMs: Long) {
        epochOriginMsByGroup[groupId] = positionMs
        epochClockMsByGroup[groupId] = nowMs()
    }

    override fun load(sources: List<StemMixerSource>) {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        }
        sources.forEach { src ->
            val k = key(src.groupId, src.stemId)
            groupOfNode[k] = src.groupId
        }
        val toFetch = sources.filter {
            val k = key(it.groupId, it.stemId)
            urlByKey[k] != it.audioUrl
        }
        if (toFetch.isEmpty()) {
            applyActiveState()
            return
        }
        val gen = ++loadGen
        scope.launch {
            val prepared = withContext(Dispatchers.Default) {
                toFetch.map { src ->
                    async {
                        val abs = resolveAbsoluteAudioUrl(src.audioUrl)
                        val tempPath = downloadAudioToCache(abs, prefix = "stem") ?: return@async null
                        Prep(src.groupId, src.stemId, src.audioUrl, tempPath)
                    }
                }.awaitAll()
            }
            if (gen != loadGen) {
                prepared.filterNotNull().forEach { p -> deleteCachedAudio(p.tempPath) }
                return@launch
            }
            prepared.filterNotNull().forEach { p ->
                val file = runCatching {
                    AVAudioFile(forReading = NSURL.fileURLWithPath(p.tempPath), error = null)
                }.getOrNull() ?: run {
                    deleteCachedAudio(p.tempPath)
                    return@forEach
                }
                val playerNode = AVAudioPlayerNode()
                val timePitch = AVAudioUnitTimePitch().apply {
                    rate = pendingRate
                    pitch = 0f
                }
                val eq = AVAudioUnitEQ(numberOfBands = 0uL)
                val stem = StemNode(playerNode, timePitch, eq, file)
                applyVolume(stem, pendingVolumes[p.stemId] ?: 1.0f)

                engine.attachNode(playerNode)
                engine.attachNode(timePitch)
                engine.attachNode(eq)
                val fmt = file.processingFormat
                engine.connect(playerNode, to = timePitch, format = fmt)
                engine.connect(timePitch, to = eq, format = fmt)
                engine.connect(eq, to = engine.mainMixerNode, format = fmt)

                val k = key(p.groupId, p.stemId)
                nodes.remove(k)?.let { old ->
                    runCatching {
                        old.playerNode.stop()
                        engine.detachNode(old.playerNode)
                        engine.detachNode(old.timePitch)
                        engine.detachNode(old.eq)
                    }
                }
                deleteTemp(k)
                scheduledKeys.remove(k)
                nodes[k] = stem
                groupOfNode[k] = p.groupId
                urlByKey[k] = p.audioUrl
                tempPathByKey[k] = p.tempPath
            }
            applyActiveState()
        }
    }

    private data class Prep(val groupId: String, val stemId: String, val audioUrl: String, val tempPath: String)

    override fun setActiveGroup(groupId: String?) {
        if (activeGroupId == groupId) return
        activeGroupId = groupId
        applyActiveState()
    }

    private fun ensureSessionActive() {
        if (sessionActivated) return
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
            sessionActivated = true
        }
    }

    private fun ensureEngineStarted() {
        if (engineStarted) return
        runCatching {
            engine.prepare()
            engine.startAndReturnError(null)
            engineStarted = true
        }
    }

    private fun scheduleFromOffset(k: String, node: StemNode, positionMs: Long) {
        val sampleRate = node.file.processingFormat.sampleRate
        val startFrame: AVAudioFramePosition =
            (positionMs.coerceAtLeast(0L) * sampleRate / 1000.0).toLong()
        val total: AVAudioFramePosition = node.file.length
        val remaining = (total - startFrame).coerceAtLeast(0L)
        if (remaining == 0L) return
        val frameCount: AVAudioFrameCount = remaining.toUInt()
        // stop() 으로 이전 schedule drop 후 새 segment schedule. 재생 중이었으면 호출자가 play() 호출.
        node.playerNode.stop()
        node.playerNode.scheduleSegment(
            node.file,
            startingFrame = startFrame,
            frameCount = frameCount,
            atTime = null,
            completionHandler = null,
        )
        scheduledKeys.add(k)
    }

    private fun applyActiveState() {
        val active = activeGroupId
        nodes.forEach { (k, stem) ->
            val isActive = groupOfNode[k] == active
            if (isActive) {
                if (k !in scheduledKeys) {
                    val pos = pendingSeekByGroup[active] ?: 0L
                    scheduleFromOffset(k, stem, pos)
                }
                if (playing) {
                    ensureSessionActive()
                    ensureEngineStarted()
                    if (!stem.playerNode.isPlaying()) stem.playerNode.play()
                } else {
                    if (stem.playerNode.isPlaying()) stem.playerNode.pause()
                }
            } else {
                if (stem.playerNode.isPlaying()) stem.playerNode.pause()
            }
        }
        if (active != null && playing) {
            cycleEpoch(active, pendingSeekByGroup[active] ?: epochOriginMsByGroup[active] ?: 0L)
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        // 0..2 그대로 보존. >1.0 은 EQ globalGain (dB) 로 부스트 — render path (ffmpeg) 와 동일 amplitude.
        val v = volume.coerceIn(0f, 2f)
        pendingVolumes[stemId] = v
        nodes.entries
            .filter { (k, _) -> k.endsWith("/$stemId") }
            .forEach { (_, stem) -> applyVolume(stem, v) }
    }

    override fun setRate(rate: Float) {
        // TimePitch.rate 유효 범위 1/32..32 (Apple doc) 이나 UI 슬라이더와 일관되게 0.5..2.0 클램프.
        val r = rate.coerceIn(0.5f, 2.0f)
        if (pendingRate == r) return
        // rate 변경 시점에 epoch 도 갱신 — estimated position 이 새 rate 로 진행되도록.
        activeGroupId?.let { g ->
            val est = estimatedPositionMs(g)
            cycleEpoch(g, est)
        }
        pendingRate = r
        nodes.values.forEach { it.timePitch.rate = r }
    }

    override fun play() {
        if (playing) return
        playing = true
        ensureSessionActive()
        ensureEngineStarted()
        applyActiveState()
    }

    override fun pause() {
        if (!playing) return
        // pause 시점 위치 캡처 — 다음 play 시 epoch 가 그 지점부터 카운트.
        activeGroupId?.let { g ->
            val est = estimatedPositionMs(g)
            cycleEpoch(g, est)
            pendingSeekByGroup[g] = est
        }
        playing = false
        nodes.values.forEach { if (it.playerNode.isPlaying()) it.playerNode.pause() }
    }

    override fun seekTo(positionMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val active = activeGroupId ?: return
        // 매 33ms tick 마다 호출되어도 자기 위치와 거의 일치하면 reschedule skip — audio glitch 회피.
        val est = estimatedPositionMs(active)
        if (kotlin.math.abs(pos - est) <= 50L) return
        pendingSeekByGroup[active] = pos
        cycleEpoch(active, pos)
        val wasPlaying = playing
        nodes.forEach { (k, stem) ->
            if (groupOfNode[k] != active) return@forEach
            scheduleFromOffset(k, stem, pos)
            if (wasPlaying) {
                ensureSessionActive()
                ensureEngineStarted()
                stem.playerNode.play()
            }
        }
    }

    override fun release() {
        loadGen++
        nodes.values.forEach { stem ->
            runCatching {
                stem.playerNode.stop()
                engine.detachNode(stem.playerNode)
                engine.detachNode(stem.timePitch)
                engine.detachNode(stem.eq)
            }
        }
        nodes.clear()
        groupOfNode.clear()
        urlByKey.clear()
        scheduledKeys.clear()
        pendingSeekByGroup.clear()
        epochOriginMsByGroup.clear()
        epochClockMsByGroup.clear()
        pendingVolumes.clear()
        tempPathByKey.values.forEach { deleteCachedAudio(it) }
        tempPathByKey.clear()
        playing = false
        activeGroupId = null
        if (engineStarted) {
            runCatching { engine.stop() }
            engineStarted = false
        }
        sessionActivated = false
        NSNotificationCenter.defaultCenter.removeObserver(interruptionObserver)
        NSNotificationCenter.defaultCenter.removeObserver(routeChangeObserver)
    }
}
