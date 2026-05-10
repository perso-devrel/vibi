package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.dubcast.shared.platform.iosCachesDirectory
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
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

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

private fun resolveAbsoluteAudioUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (!url.startsWith("/")) return url
    val baseUrl = (NSBundle.mainBundle.objectForInfoDictionaryKey("BFFBaseURL") as? String)
        ?.takeIf { it.isNotEmpty() } ?: return url
    return "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
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
    private var activeGroupId: String? = null
    private var playing = false
    private var loadGen = 0

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    /** key → audioUrl. 같은 key 라도 url 이 변하면 (token refresh 등) 재다운로드. */
    private val urlByKey = mutableMapOf<String, String>()
    /** key → 임시 파일 path. release / url refresh 시 삭제로 caches 누적 회수. */
    private val tempPathByKey = mutableMapOf<String, String>()

    private fun deleteTemp(key: String) {
        tempPathByKey.remove(key)?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
    }

    override fun load(sources: List<StemMixerSource>) {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
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
            val cachesDir = iosCachesDirectory() ?: return@launch
            val prepared = withContext(Dispatchers.Default) {
                toFetch.map { src ->
                    async {
                        val abs = resolveAbsoluteAudioUrl(src.audioUrl)
                        val nsUrl = NSURL.URLWithString(abs) ?: return@async null
                        val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@async null
                        val ext = abs.substringAfterLast('.', "").substringBefore('?').lowercase()
                            .ifEmpty { "audio" }
                        val tempPath = "$cachesDir/stem_${NSUUID().UUIDString()}.$ext"
                        @Suppress("CAST_NEVER_SUCCEEDS")
                        if (!data.writeToFile(tempPath, atomically = true)) return@async null
                        Prep(src.groupId, src.stemId, src.audioUrl, tempPath)
                    }
                }.awaitAll()
            }
            if (gen != loadGen) {
                // stale 결과 — 임시 파일 누수 회수.
                prepared.filterNotNull().forEach { p ->
                    NSFileManager.defaultManager.removeItemAtPath(p.tempPath, null)
                }
                return@launch
            }
            prepared.filterNotNull().forEach { p ->
                val player = runCatching {
                    AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(p.tempPath), error = null)
                }.getOrNull() ?: run {
                    NSFileManager.defaultManager.removeItemAtPath(p.tempPath, null)
                    return@forEach
                }
                player.numberOfLoops = 0
                player.volume = pendingVolumes[p.stemId] ?: 1.0f
                player.prepareToPlay()
                val k = key(p.groupId, p.stemId)
                // 이미 있는 player (url refresh) 면 release + 옛 임시 파일 삭제 후 교체.
                players.remove(k)?.let {
                    it.delegate = null
                    it.stop()
                }
                deleteTemp(k)
                players[k] = player
                groupOfPlayer[k] = p.groupId
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

    private fun applyActiveState() {
        // active group player 만 playing 상태에 따라 play/pause, 나머지는 항상 pause.
        players.forEach { (k, p) ->
            val isActive = groupOfPlayer[k] == activeGroupId
            if (isActive && playing) p.play() else p.pause()
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
        // 모든 임시 파일 회수.
        tempPathByKey.values.forEach {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
        tempPathByKey.clear()
        playing = false
        activeGroupId = null
    }
}
