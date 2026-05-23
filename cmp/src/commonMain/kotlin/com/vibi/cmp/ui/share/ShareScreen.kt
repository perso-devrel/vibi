package com.vibi.cmp.ui.share

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
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.ui.share.ShareViewModel
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
        Text("Export complete", style = MaterialTheme.typography.headlineSmall)
        Text("Output: $outputPath")

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.shareVideo() },
            enabled = !state.isSharing && outputPath.isNotEmpty()
        ) {
            Text(if (state.isSharing) "Opening share sheet…" else "Share")
        }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.saveToGallery() },
            enabled = !state.isSaving && !state.savedToGallery
        ) {
            Text(
                when {
                    state.isSaving -> "Saving…"
                    state.savedToGallery -> "Saved to library"
                    else -> "Save to library"
                }
            )
        }

        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
    }
}
