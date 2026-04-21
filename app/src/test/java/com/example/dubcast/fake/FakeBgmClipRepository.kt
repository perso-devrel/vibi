package com.example.dubcast.fake

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.repository.BgmClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBgmClipRepository : BgmClipRepository {

    private val clips = MutableStateFlow<List<BgmClip>>(emptyList())

    override fun observeClips(projectId: String): Flow<List<BgmClip>> =
        clips.map { list -> list.filter { it.projectId == projectId }.sortedBy { it.startMs } }

    override suspend fun getClip(clipId: String): BgmClip? {
        return clips.value.find { it.id == clipId }
    }

    override suspend fun addClip(clip: BgmClip) {
        clips.value = clips.value + clip
    }

    override suspend fun updateClip(clip: BgmClip) {
        clips.value = clips.value.map { if (it.id == clip.id) clip else it }
    }

    override suspend fun deleteClip(clipId: String) {
        clips.value = clips.value.filter { it.id != clipId }
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        clips.value = clips.value.filter { it.projectId != projectId }
    }

    fun all(): List<BgmClip> = clips.value
}
