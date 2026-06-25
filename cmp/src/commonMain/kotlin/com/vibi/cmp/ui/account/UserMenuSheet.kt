package com.vibi.cmp.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.platform.RuntimeFlags
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.shared.domain.model.AuthUser
import com.vibi.shared.ui.account.UserMenuViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 우상단 아바타 탭 시 열리는 메뉴 sheet.
 *
 * 진입 시점에 BFF 잔액을 1회 refresh — InputScreen 재진입마다 fetch 하지 않도록.
 * 로그아웃 또는 회원탈퇴 완료 시 [onSignedOut] 호출.
 *
 * 크레딧 구매 진입점은 [RuntimeFlags.iapEnabled] = false 일 때 숨겨진다. App Store 심사 통과
 * 전 (StoreKit 미연동) 상태로 IAP UI 노출 시 가이드라인 2.1 / 3.1.1 reject 위험.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMenuSheet(
    onDismiss: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: UserMenuViewModel = koinViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.uiState.collectAsState()
    val tokens = LocalVibiColors.current
    var purchaseOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        // 잔액은 결제(iapEnabled) 와 무관하게 갱신 — 무료 선출시에도 남은 무료 분리 횟수를
        // 최신으로 표시. 구매/충전 진입만 iapEnabled 로 게이팅.
        viewModel.refreshBalance()
        viewModel.navigateToLogin.collect { onSignedOut() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileHeader(
                user = state.user,
                credits = state.credits,
                // 잔액은 항상 노출(무료 분리 잔여 표시). 구매 진입(BuyCreditsRow)만 iapEnabled 게이팅.
                showCredits = true,
            )

            if (RuntimeFlags.iapEnabled) {
                BuyCreditsRow(onClick = { purchaseOpen = true })
            } else {
                ResearchPreviewNote()
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sign out",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = tokens.mutedText,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.signOut() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    text = "Delete account",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (purchaseOpen) {
        CreditPurchaseSheet(
            products = viewModel.products,
            currentCredits = state.credits,
            isAdmin = state.isAdmin,
            onDismiss = { purchaseOpen = false },
            onPurchased = { product, platform, receipt, transactionId ->
                viewModel.purchaseCredits(
                    product = product,
                    platform = platform,
                    receipt = receipt,
                    transactionId = transactionId,
                )
            },
            onAdminGrant = viewModel::adminGrantCredits,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = {
                // 실제 동작과 일치시킨 고지(App Store 5.1.1 투명성): BFF 가 서버 계정을 영구
                // 삭제하고 이 기기 세션을 종료한다. 로컬 프로젝트 row 는 userId 격리로 더는
                // 접근 불가(같은 계정 재로그인 불가)하므로 "기기에서 삭제"로 단정하지 않는다.
                Text(
                    "Your account will be permanently deleted and you'll be signed out. This can't be undone."
                )
            },
            confirmButton = {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            confirmDelete = false
                            viewModel.deleteAccount()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "Cancel",
                    modifier = Modifier
                        .clickable { confirmDelete = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
        )
    }
}

@Composable
private fun ProfileHeader(user: AuthUser?, credits: Int, showCredits: Boolean) {
    val tokens = LocalVibiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = user, size = 56.dp, initialFontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.name?.takeIf { it.isNotBlank() } ?: "Guest",
                style = TextStyle(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = user?.email?.takeIf { it.isNotBlank() } ?: "Not signed in",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = tokens.mutedText,
                ),
                maxLines = 1,
            )
        }
        if (showCredits) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Credits",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = tokens.mutedText,
                        letterSpacing = 0.4.sp,
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$credits",
                    style = TextStyle(
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                )
            }
        }
    }
}

/**
 * IAP 미오픈([RuntimeFlags.iapEnabled]=false) 기간, [BuyCreditsRow] 자리를 대체하는 상시 안내.
 *
 * 결제가 없는 이유(리서치 프리뷰)와 무료 체험량(가입 시 10분 = [CreditRepository.SIGNUP_BONUS_CREDITS]
 * 크레딧, 1크레딧=1분)을 알려 잔액 칩의 맥락을 보강한다. 비클릭 정보 카드 — "coming soon/구매" 같이
 * 아직 없는 유료 기능을 암시하는 표현은 App Store 2.3.1 회피 위해 배제.
 *
 * 무료 체험 분량이 [CreditRepository.SIGNUP_BONUS_CREDITS] 와 어긋나지 않게, 값 변경 시 함께 갱신.
 */
@Composable
private fun ResearchPreviewNote() {
    val tokens = LocalVibiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.chipBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tokens.accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Research preview",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Enjoy 10 minutes of audio separation, free while we're in preview.",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = tokens.mutedText,
                )
            )
        }
    }
}

@Composable
private fun BuyCreditsRow(onClick: () -> Unit) {
    val tokens = LocalVibiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tokens.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Buy credits",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "For audio separation",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = tokens.mutedText,
                )
            )
        }
        Text(
            text = "›",
            style = TextStyle(fontSize = 22.sp, color = tokens.mutedText)
        )
    }
}
