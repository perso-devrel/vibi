package com.dubcast.cmp.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.ui.share.ShareViewModel
import org.koin.compose.koinInject

/**
 * Share 화면 (M3 마이그레이션 placeholder).
 *
 * legacy [com.example.dubcast.ui.share.ShareScreen] 의 등가. iOS 의 GallerySaver
 * actual 구현은 M4 시점에 별도 작업.
 */
@Composable
fun ShareScreen(
    outputPath: String
) {
    val gallerySaver: GallerySaver = koinInject()
    val viewModel = remember(outputPath) {
        ShareViewModel(outputPath = outputPath, gallerySaver = gallerySaver)
    }
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("내보내기 완료", style = MaterialTheme.typography.headlineSmall)
        Text("출력 경로: $outputPath")

        Button(
            onClick = { viewModel.saveToGallery() },
            enabled = !state.isSaving && !state.savedToGallery
        ) {
            Text(
                when {
                    state.isSaving -> "저장 중…"
                    state.savedToGallery -> "갤러리에 저장됨"
                    else -> "갤러리에 저장"
                }
            )
        }

        state.error?.let { Text("오류: $it") }
    }
}
