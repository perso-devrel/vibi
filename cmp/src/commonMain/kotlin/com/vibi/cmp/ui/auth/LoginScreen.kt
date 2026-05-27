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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.BiasAlignment
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

    // 상단 — 로고만 중앙. 하단 — Google/Apple 버튼 + ToS. 두 영역을 Box align 으로 분리.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        // 이전 레이아웃 (Column verticalArrangement.Center 안 logo + 48dp + 버튼들) 에서 로고는
        // 컬럼 상단에 있어 실제 위치는 화면 위쪽 ~1/3. BiasAlignment(-0.3) 로 그 위치 복원.
        Text(
            text = "vibi",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(BiasAlignment(0f, -0.3f)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .padding(bottom = 32.dp),
        ) {
            (state as? LoginViewModel.UiState.Error)?.let { err ->
                Text(
                    text = err.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }

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

            Spacer(Modifier.height(20.dp))
            // App Store 가이드라인 5.1.1 데이터 수집 고지.
            Text(
                text = "By signing in, you agree to our Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
