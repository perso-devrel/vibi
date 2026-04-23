package com.example.dubcast.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.Stem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSeparationSheet(
    state: AudioSeparationUiState,
    onDismiss: () -> Unit,
    onSpeakersChange: (Int) -> Unit,
    onStart: () -> Unit,
    onToggleStem: (stemId: String) -> Unit,
    onStemVolumeChange: (stemId: String, volume: Float) -> Unit,
    onToggleMuteOriginal: () -> Unit,
    onConfirmMix: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("음원 분리", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            when (state.step) {
                AudioSeparationStep.SETUP -> SetupStep(
                    state = state,
                    onSpeakersChange = onSpeakersChange,
                    onStart = onStart,
                    onDismiss = onDismiss
                )
                AudioSeparationStep.PROCESSING -> ProcessingStep(state)
                AudioSeparationStep.PICK_STEMS -> PickStemsStep(
                    state = state,
                    onToggleStem = onToggleStem,
                    onStemVolumeChange = onStemVolumeChange,
                    onToggleMuteOriginal = onToggleMuteOriginal,
                    onConfirmMix = onConfirmMix,
                    onDismiss = onDismiss
                )
                AudioSeparationStep.MIXING -> MixingStep(state)
                AudioSeparationStep.DONE -> DoneStep(onDismiss)
                AudioSeparationStep.FAILED -> FailedStep(state, onDismiss)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SetupStep(
    state: AudioSeparationUiState,
    onSpeakersChange: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = "화자 수: ${state.numberOfSpeakers}",
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = state.numberOfSpeakers.toFloat(),
        onValueChange = { onSpeakersChange(it.toInt()) },
        valueRange = 1f..10f,
        steps = 8
    )
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "⚠ 음원 분리를 진행하면 현재까지의 편집이 기준점으로 확정되어 되돌릴 수 없습니다. " +
                "분리 이후의 편집만 실행취소가 가능합니다.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("취소") }
        Button(
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier.weight(1f)
        ) { Text("분리 시작") }
    }
}

@Composable
private fun ProcessingStep(state: AudioSeparationUiState) {
    Text(
        text = localizeProgressReason(state.progressReason),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Text("${state.progress}%", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun PickStemsStep(
    state: AudioSeparationUiState,
    onToggleStem: (stemId: String) -> Unit,
    onStemVolumeChange: (stemId: String, volume: Float) -> Unit,
    onToggleMuteOriginal: () -> Unit,
    onConfirmMix: () -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = "합성할 트랙을 선택하세요. 합성하면 원본 분리 결과는 삭제되므로 신중히 선택하세요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    state.stems.forEach { stem ->
        StemRow(
            stem = stem,
            selection = state.selections[stem.stemId],
            onToggle = { onToggleStem(stem.stemId) },
            onVolumeChange = { onStemVolumeChange(stem.stemId, it) }
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.muteOriginalSegmentAudio,
            onCheckedChange = { onToggleMuteOriginal() }
        )
        Text(
            text = "원본 세그먼트 오디오 음소거",
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("취소") }
        Button(
            onClick = onConfirmMix,
            enabled = state.canMix,
            modifier = Modifier.weight(1f)
        ) { Text("합성") }
    }
}

@Composable
private fun StemRow(
    stem: Stem,
    selection: StemSelectionUi?,
    onToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selection?.selected == true,
                onCheckedChange = { onToggle() }
            )
            Text(
                text = stemDisplayLabel(stem),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (selection?.selected == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "볼륨",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = selection.volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..2f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(selection.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MixingStep(state: AudioSeparationUiState) {
    Text("합성 중…", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { state.mixProgress.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Text("${state.mixProgress}%", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun DoneStep(onDismiss: () -> Unit) {
    Text("완료되었습니다. BGM 트랙에 합성 결과가 추가되었습니다.")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
}

@Composable
private fun FailedStep(state: AudioSeparationUiState, onDismiss: () -> Unit) {
    Text(
        text = state.errorMessage ?: "알 수 없는 오류가 발생했습니다",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
}
