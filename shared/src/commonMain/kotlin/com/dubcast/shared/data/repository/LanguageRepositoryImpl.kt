package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.domain.model.SupportedLanguage
import com.dubcast.shared.domain.repository.LanguageRepository

class LanguageRepositoryImpl(
    private val api: BffApi
) : LanguageRepository {

    override suspend fun fetchLanguages(): Result<List<SupportedLanguage>> = runCatching {
        api.getLanguages().languages.map { dto ->
            SupportedLanguage(
                code = dto.code,
                name = dto.name,
                nativeName = dto.nativeName,
                supportsDubbing = dto.supportsDubbing,
                supportsSubtitles = dto.supportsSubtitles,
            )
        }
    }
}
