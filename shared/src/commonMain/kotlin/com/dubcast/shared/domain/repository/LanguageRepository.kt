package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.SupportedLanguage

interface LanguageRepository {
    /** BFF `/api/v2/languages` 호출. Perso 가 지원하는 타깃 언어 목록. */
    suspend fun fetchLanguages(): Result<List<SupportedLanguage>>
}
