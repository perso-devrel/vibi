package com.example.dubcast.fake

import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.ImageClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeImageClipRepository : ImageClipRepository {

    private val clips = MutableStateFlow<List<ImageClip>>(emptyList())

    override fun observeClips(projectId: String): Flow<List<ImageClip>> =
        clips.map { list -> list.filter { it.projectId == projectId } }

    override suspend fun addClip(clip: ImageClip) {
        clips.value = clips.value + clip
    }

    override suspend fun updateClip(clip: ImageClip) {
        clips.value = clips.value.map { if (it.id == clip.id) clip else it }
    }

    override suspend fun deleteClip(clipId: String) {
        clips.value = clips.value.filter { it.id != clipId }
    }

    override suspend fun deleteAllClips(projectId: String) {
        clips.value = clips.value.filter { it.projectId != projectId }
    }

    override suspend fun getClip(clipId: String): ImageClip? {
        return clips.value.find { it.id == clipId }
    }
}
