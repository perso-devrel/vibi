package com.example.dubcast.fake

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.repository.DubClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDubClipRepository : DubClipRepository {

    private val clips = MutableStateFlow<List<DubClip>>(emptyList())

    override fun observeClips(projectId: String): Flow<List<DubClip>> =
        clips.map { list -> list.filter { it.projectId == projectId } }

    override suspend fun addClip(clip: DubClip) {
        clips.value = clips.value + clip
    }

    override suspend fun updateClip(clip: DubClip) {
        clips.value = clips.value.map { if (it.id == clip.id) clip else it }
    }

    override suspend fun deleteClip(clipId: String) {
        clips.value = clips.value.filter { it.id != clipId }
    }

    override suspend fun deleteAllClips(projectId: String) {
        clips.value = clips.value.filter { it.projectId != projectId }
    }

    override suspend fun getClip(clipId: String): DubClip? {
        return clips.value.find { it.id == clipId }
    }
}
