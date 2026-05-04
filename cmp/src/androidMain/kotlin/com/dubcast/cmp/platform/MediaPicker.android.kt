package com.dubcast.cmp.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun MediaPicker(
    label: String,
    onPicked: (uri: String) -> Unit
) {
    val launch = rememberMediaPickerLauncher(onPicked)
    Button(onClick = { launch() }) {
        Text(label)
    }
}

@Composable
actual fun rememberMediaPickerLauncher(
    onPicked: (uri: String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
    }
}
