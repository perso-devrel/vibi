package com.example.dubcast.ui.input

import android.net.Uri
import com.example.dubcast.domain.model.ValidationResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dubcast.domain.model.ValidationError

@Composable
fun InputScreen(
    onNavigateToTimeline: (projectId: String) -> Unit,
    viewModel: InputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToTimeline.collect { projectId ->
            onNavigateToTimeline(projectId)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVideoPicked(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DubCast",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a video to edit with AI dubbing",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
        ) {
            Text("Select Video")
        }

        Spacer(modifier = Modifier.height(16.dp))

        state.selectedVideo?.let { video ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = video.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(text = "${video.durationMs / 1000}s | ${video.width}x${video.height}")
                    Text(text = "Format: ${video.mimeType}")
                    Text(text = "Size: ${video.sizeBytes / 1024 / 1024}MB")
                }
            }
        }

        state.validationResult?.let { result ->
            when (result) {
                is ValidationResult.Invalid -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (result.reason) {
                            ValidationError.UNSUPPORTED_FORMAT -> "Unsupported format. Use MP4, MOV, or WebM."
                            ValidationError.DURATION_EXCEEDS_LIMIT -> "Video must be 10 minutes or shorter."
                            ValidationError.RESOLUTION_EXCEEDS_LIMIT -> "Resolution must be 1080p or lower."
                            ValidationError.METADATA_UNREADABLE -> "Could not read video metadata."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ValidationResult.Valid -> {}
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.onContinue() },
            enabled = state.validationResult == ValidationResult.Valid
        ) {
            Text("Continue")
        }
    }
}
