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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.shared.domain.model.AuthUser
import com.vibi.shared.ui.account.UserMenuViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 우상단 아바타 탭 시 열리는 메뉴 sheet.
 *
 * 진입 시점에 BFF 잔액을 1회 refresh — InputScreen 재진입마다 fetch 하지 않도록.
 * 로그아웃 또는 회원탈퇴 완료 시 [onSignedOut] 호출.
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
    var purchaseOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.refreshBalance()
        viewModel.navigateToLogin.collect { onSignedOut() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileHeader(user = state.user, credits = state.credits)

            BuyCreditsRow(onClick = { purchaseOpen = true })

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "로그아웃",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = Color(0xCCEBEBF5),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.signOut() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    text = "회원탈퇴",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = Color(0xFFFF453A),
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
            onDismiss = { purchaseOpen = false },
            onPurchased = { product, platform, receipt, transactionId ->
                viewModel.purchaseCredits(
                    product = product,
                    platform = platform,
                    receipt = receipt,
                    transactionId = transactionId,
                )
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("회원탈퇴") },
            text = {
                Text(
                    "계정과 이 기기에 저장된 모든 작업이 삭제됩니다. 이 작업은 되돌릴 수 없습니다."
                )
            },
            confirmButton = {
                Text(
                    text = "탈퇴",
                    color = Color(0xFFFF453A),
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
                    text = "취소",
                    modifier = Modifier
                        .clickable { confirmDelete = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            containerColor = Color(0xFF2C2C2E),
            titleContentColor = Color.White,
            textContentColor = Color(0xCCEBEBF5),
        )
    }
}

@Composable
private fun ProfileHeader(user: AuthUser?, credits: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = user, size = 56.dp, initialFontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.name?.takeIf { it.isNotBlank() } ?: "게스트",
                style = TextStyle(
                    fontSize = 17.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = user?.email?.takeIf { it.isNotBlank() } ?: "로그인된 계정 없음",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = Color(0x99EBEBF5),
                ),
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "보유 크레딧",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = Color(0x99EBEBF5),
                    letterSpacing = 0.4.sp,
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$credits",
                style = TextStyle(
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            )
        }
    }
}

@Composable
private fun BuyCreditsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2C2C2E))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A84FF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "크레딧 구매",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "음원 분리 · 자동 더빙에 사용",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color(0x99EBEBF5),
                )
            )
        }
        Text(
            text = "›",
            style = TextStyle(fontSize = 22.sp, color = Color(0x66EBEBF5))
        )
    }
}
