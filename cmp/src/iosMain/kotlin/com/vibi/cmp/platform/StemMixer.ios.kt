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
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL

/**
 * iOS: directive group 별 AVAudioPlayer 사전 prepare + active group 만 play.
 * directive 전환 시 다운로드/init 끊김 없음 — 모든 group 의 player 가 이미 ready.
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

    /** key = "groupId/stemId" — 같은 stemId 라도 다른 group 이면 별도 player. */
    private val players = mutableMapOf<String, AVAudioPlayer>()
    /** key = composite (위와 동일), value = (groupId, stemId). */
    private val groupOfPlayer = mutableMapOf<String, String>()
    /** stemId → 적용된 마지막 volume. 새 player 생성 시 적용. */
    private val pendingVolumes = mutableMapOf<String, Float>()
    /** groupId → 마지막 seek 위치(ms). 새 player 가 download 후 올바른 offset 에서 시작. */
    private val pendingSeekByGroup = mutableMapOf<String, Long>()
    /** 이미 prepareToPlay() 호출된 player key. inactive group 은 미준비 상태로 유지. */
    private val preparedKeys = mutableSetOf<String>()
    private var activeGroupId: String? = null
    private var playing = false
    private var loadGen = 0
    /** AVAudioSession.setActive(true) 가 호출됐는지. load 시 즉시 활성화하면 영상 AVPlayer
     *  init 와 같은 cycle 에 hardware audio unit 경합 → IOWorkLoop overload / Fig parser 에러.
     *  실제 play() 시점에 lazy activate. */
    private var sessionActivated = false

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    /** key → audioUrl. 같은 key 라도 url 이 변하면 (token refresh 등) 재다운로드. */
    private val urlByKey = mutableMapOf<String, String>()
    /** key → 임시 파일 path. release / url refresh 시 삭제로 caches 누적 회수. */
    private val tempPathByKey = mutableMapOf<String, String>()

    private fun deleteTemp(key: String) {
        tempPathByKey.remove(key)?.let { deleteCachedAudio(it) }
    }

    override fun load(sources: List<StemMixerSource>) {
        // category 만 미리 설정 (idempotent). setActive(true) 는 play() 시점에 lazy —
        // 진입 시 영상 AVPlayer init 와 같은 cycle 에 audio hardware 잡지 않도록.
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        }

        // 누적 cache — 기존 player 는 release 안 하고 keep. caller 가 activeDirective 의 sources 만
        // 매번 load 해도 다른 directive 의 player 는 cache 에 남아있어 재진입 시 즉시 활성.
        // 1. group mapping 갱신 (이미 player 있는 source 도 group 이 바뀌었을 수 있음).
        sources.forEach { src ->
            val k = key(src.groupId, src.stemId)
            groupOfPlayer[k] = src.groupId
        }
        // 3. 추가/url 변경된 source 만 다운로드.
        val toFetch = sources.filter {
            val k = key(it.groupId, it.stemId)
            urlByKey[k] != it.audioUrl  // 신규 또는 url refresh
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
                // stale 결과 — 임시 파일 누수 회수.
                prepared.filterNotNull().forEach { p -> deleteCachedAudio(p.tempPath) }
                return@launch
            }
            prepared.filterNotNull().forEach { p ->
                val player = runCatching {
                    AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(p.tempPath), error = null)
                }.getOrNull() ?: run {
                    deleteCachedAudio(p.tempPath)
                    return@forEach
                }
                player.numberOfLoops = 0
                player.volume = pendingVolumes[p.stemId] ?: 1.0f
                // prepareToPlay() 는 applyActiveState() 에서 active group 의 player 에만 호출.
                // 모든 player 가 동시에 prepare 하면 hardware audio unit 경합으로 IOWorkLoop overload.
                val k = key(p.groupId, p.stemId)
                // 이미 있는 player (url refresh) 면 release + 옛 임시 파일 삭제 후 교체.
                players.remove(k)?.let {
                    it.delegate = null
                    it.stop()
                }
                deleteTemp(k)
                preparedKeys.remove(k)
                players[k] = player
                groupOfPlayer[k] = p.groupId
                urlByKey[k] = p.audioUrl
                tempPathByKey[k] = p.tempPath
                // download 완료 전에 seekTo 가 호출됐다면 그 위치에서 시작.
                pendingSeekByGroup[p.groupId]?.let { posMs ->
                    player.currentTime = posMs.coerceAtLeast(0L) / 1000.0
                }
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
            AVAudioSession.sharedInstance().setActive(true, null)
            sessionActivated = true
        }
    }

    private fun applyActiveState() {
        // active group player 만 playing 상태에 따라 play/pause, 나머지는 항상 pause.
        // 첫 활성화 시 lazy prepareToPlay — inactive group 은 미준비 유지로 audio unit 경합 회피.
        players.forEach { (k, p) ->
            val isActive = groupOfPlayer[k] == activeGroupId
            if (isActive) {
                if (preparedKeys.add(k)) p.prepareToPlay()
                if (playing) {
                    ensureSessionActive()
                    p.play()
                } else {
                    p.pause()
                }
            } else {
                p.pause()
            }
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        pendingVolumes[stemId] = v
        // 같은 stemId 가 여러 group 에 있을 수 있어 모두 적용.
        players.entries
            .filter { (k, _) -> k.endsWith("/$stemId") }
            .forEach { (_, p) -> p.volume = v }
    }

    override fun play() {
        if (playing) return
        playing = true
        ensureSessionActive()
        applyActiveState()
    }

    override fun pause() {
        if (!playing) return
        playing = false
        players.values.forEach { it.pause() }
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        val active = activeGroupId ?: return
        pendingSeekByGroup[active] = positionMs.coerceAtLeast(0L)
        players.forEach { (k, p) ->
            if (groupOfPlayer[k] == active) p.currentTime = seconds
        }
    }

    override fun release() {
        loadGen++
        players.values.forEach {
            it.delegate = null
            it.stop()
        }
        players.clear()
        groupOfPlayer.clear()
        urlByKey.clear()
        preparedKeys.clear()
        pendingSeekByGroup.clear()
        // 모든 임시 파일 회수.
        tempPathByKey.values.forEach { deleteCachedAudio(it) }
        tempPathByKey.clear()
        playing = false
        activeGroupId = null
        // session 은 process-lifecycle. 다른 player 가 잡고 있을 수 있으니 setActive(false) 안 함.
        sessionActivated = false
    }
}
