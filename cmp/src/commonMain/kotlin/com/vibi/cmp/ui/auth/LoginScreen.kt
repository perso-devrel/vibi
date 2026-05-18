package com.vibi.cmp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.shared.ui.auth.LoginViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val loadingProvider = (state as? LoginViewModel.UiState.Loading)?.provider
    val anyLoading = loadingProvider != null

    LaunchedEffect(viewModel) {
        viewModel.navigateToHome.collect { onSignedIn() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        ) {
            Text(
                text = "vibi",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = viewModel::signInWithGoogle,
                enabled = !anyLoading,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
            ) {
                if (loadingProvider == LoginViewModel.Provider.Google) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Sign in with Google")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Apple Human Interface Guidelines — 검정 배경 + 흰색 텍스트, 최소 44pt 높이.
            // 다크/라이트 테마 무관하게 Apple 표준 외형 유지.
            Button(
                onClick = viewModel::signInWithApple,
                enabled = !anyLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Black.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
            ) {
                if (loadingProvider == LoginViewModel.Provider.Apple) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Sign in with Apple")
                }
            }

            (state as? LoginViewModel.UiState.Error)?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = err.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            // App Store 가이드라인 5.1.1 데이터 수집 고지. URL 핸들러 도입 시 clickable 링크로 전환 —
            // empty onClick 링크는 심사 reject 사유이므로 현재는 plain text.
            Text(
                text = "Sign In 시 이용약관 및 개인정보 처리방침에 동의하는 것으로 간주됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
