package com.dubcast.cmp.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.domain.usecase.share.ShareSheetLauncher
import com.dubcast.shared.ui.share.ShareViewModel
import org.koin.compose.koinInject

@Composable
fun ShareScreen(
    outputPath: String
) {
    val gallerySaver: GallerySaver = koinInject()
    val shareSheetLauncher: ShareSheetLauncher = koinInject()
    val viewModel = remember(outputPath) {
        ShareViewModel(
            outputPath = outputPath,
            gallerySaver = gallerySaver,
            shareSheetLauncher = shareSheetLauncher
        )
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
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.shareVideo() },
            enabled = !state.isSharing && outputPath.isNotEmpty()
        ) {
            Text(if (state.isSharing) "공유 시트 여는 중…" else "공유")
        }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
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

        state.error?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }
    }
}
