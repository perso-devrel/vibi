package com.example.dubcast.fake

import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.repository.SubtitleClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSubtitleClipRepository : SubtitleClipRepository {

    private val clips = MutableStateFlow<List<SubtitleClip>>(emptyList())

    override fun observeClips(projectId: String): Flow<List<SubtitleClip>> =
        clips.map { list -> list.filter { it.projectId == projectId } }

    override suspend fun addClip(clip: SubtitleClip) {
        clips.value = clips.value + clip
    }

    override suspend fun updateClip(clip: SubtitleClip) {
        clips.value = clips.value.map { if (it.id == clip.id) clip else it }
    }

    override suspend fun deleteClip(clipId: String) {
        clips.value = clips.value.filter { it.id != clipId }
    }

    override suspend fun deleteAllClips(projectId: String) {
        clips.value = clips.value.filter { it.projectId != projectId }
    }

    override suspend fun getClip(clipId: String): SubtitleClip? {
        return clips.value.find { it.id == clipId }
    }
}
