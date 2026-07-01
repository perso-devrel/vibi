package com.vibi.cmp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.legal.LegalUrls
import com.vibi.cmp.legal.appendLegalLink
import com.vibi.cmp.platform.isIosPlatform
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(BiasAlignment(0f, -0.3f)),
        ) {
            Text(
                text = "VIBI",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "AI Sound Eraser",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }

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

            GoogleSignInButton(
                onClick = viewModel::signInWithGoogle,
                enabled = !anyLoading,
                loading = loadingProvider == LoginViewModel.Provider.Google,
                modifier = Modifier.fillMaxWidth(),
            )

            // Apple 로그인은 iOS 한정 — Android 엔 Apple 네이티브 SDK 가 없어 버튼 자체를 숨긴다.
            if (isIosPlatform) {
                Spacer(Modifier.height(12.dp))

                AppleSignInButton(
                    onClick = viewModel::signInWithApple,
                    enabled = !anyLoading,
                    loading = loadingProvider == LoginViewModel.Provider.Apple,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            // App Store 가이드라인 5.1.1 데이터 수집 고지 — ToS/개인정보 클릭 가능 링크.
            val linkColor = MaterialTheme.colorScheme.primary
            val agreement = buildAnnotatedString {
                append("By signing in, you agree to our ")
                appendLegalLink("Terms of Service", LegalUrls.TERMS, linkColor)
                append(" and ")
                appendLegalLink("Privacy Policy", LegalUrls.PRIVACY, linkColor)
                append(".")
            }
            Text(
                text = agreement,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
