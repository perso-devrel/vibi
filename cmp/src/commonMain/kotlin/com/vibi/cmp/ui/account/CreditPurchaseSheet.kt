package com.vibi.cmp.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.platform.PurchaseLauncher
import com.vibi.cmp.platform.PurchaseResult
import com.vibi.shared.domain.model.IapPlatform
import com.vibi.shared.ui.account.CreditProduct
import kotlinx.coroutines.launch

/**
 * 크레딧 구매 storefront — **App Store / Play Store 심사 통과 가능한 디자인**.
 *
 * 가이드라인 핵심:
 *  - Apple 시스템 결제 popup (Face ID / Touch ID prompt, "구매하시겠습니까") 은 **앱이 그리지 않는다**.
 *    실제 popup 은 OS 가 띄움 — 본 sheet 는 "구매" 버튼 트리거까지만.
 *  - 비소비성 / 구독이 아닐 때도 ["구매 복원"] 노출 — Apple 가이드라인 3.1.1 권장.
 *  - 약관 / 개인정보 처리방침 / "Apple ID 로 청구" 안내문구 포함 — 가이드라인 3.1.2 / 5.1.1.
 *
 * 의도적으로 Apple 시스템 dialog 스타일 (작은 회색 popup) 은 피하고, App Store 의
 * "구독 페이지" 처럼 큰 storefront 모달로 디자인.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditPurchaseSheet(
    products: List<CreditProduct>,
    currentCredits: Int,
    onDismiss: () -> Unit,
    onPurchased: (product: CreditProduct, platform: IapPlatform, receipt: String, transactionId: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val launcher = remember { PurchaseLauncher() }
    val scope = rememberCoroutineScope()

    var selected by remember(products) {
        mutableStateOf(products.firstOrNull { it.highlight } ?: products.firstOrNull())
    }
    var status by remember { mutableStateOf<Status>(Status.Idle) }

    ModalBottomSheet(
        onDismissRequest = { if (status != Status.Purchasing) onDismiss() },
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(currentCredits = currentCredits)

            HeroBanner()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                products.forEach { product ->
                    ProductRow(
                        product = product,
                        selected = product.productId == selected?.productId,
                        onClick = { if (status != Status.Purchasing) selected = product },
                    )
                }
            }

            PurchaseCta(
                productLabel = selected?.priceLabel,
                isPurchasing = status == Status.Purchasing,
                onClick = {
                    val target = selected ?: return@PurchaseCta
                    status = Status.Purchasing
                    scope.launch {
                        status = launcher.purchase(target.productId).toStatus { success ->
                            onPurchased(target, success.platform, success.receipt, success.transactionId)
                        }
                    }
                }
            )

            when (val s = status) {
                is Status.Error -> Text(
                    text = "⚠ ${s.message}",
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFFFF453A)),
                )
                Status.Success -> Text(
                    text = "✓ 구매가 완료되었습니다.",
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFF30D158)),
                )
                else -> Unit
            }

            RestoreRow(
                isWorking = status == Status.Restoring,
                onClick = {
                    if (status == Status.Restoring) return@RestoreRow
                    status = Status.Restoring
                    scope.launch {
                        status = launcher.restorePurchases().toStatus { /* restore: no side effect */ }
                    }
                }
            )

            FinePrint()
        }
    }

    LaunchedEffect(status) {
        if (status is Status.Success) {
            // 짧게 노출 후 sheet 닫기 — 사용자가 결과를 본 뒤 자동 dismiss.
            kotlinx.coroutines.delay(900)
            onDismiss()
        }
    }
}

private sealed interface Status {
    data object Idle : Status
    data object Purchasing : Status
    data object Restoring : Status
    data object Success : Status
    data class Error(val message: String) : Status
}

/**
 * PurchaseResult → Status 매핑 공통 — Success 시 [onSuccess] 콜백 후 Success 상태로,
 * UserCancelled 면 Idle, Failed 면 Error 로 전이. purchase / restorePurchases 양쪽이 공유.
 */
private inline fun PurchaseResult.toStatus(onSuccess: (PurchaseResult.Success) -> Unit): Status =
    when (this) {
        is PurchaseResult.Success -> { onSuccess(this); Status.Success }
        is PurchaseResult.UserCancelled -> Status.Idle
        is PurchaseResult.Failed -> Status.Error(message)
    }

@Composable
private fun Header(currentCredits: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(
            text = "크레딧 구매",
            style = TextStyle(
                fontSize = 22.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "현재 보유 · $currentCredits 크레딧",
            style = TextStyle(fontSize = 13.sp, color = Color(0x99EBEBF5)),
        )
    }
}

@Composable
private fun HeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF6E45E2), Color(0xFF88D3CE))
                )
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    style = TextStyle(fontSize = 22.sp, color = Color.White)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "음원만 더 깔끔하게",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "분리 · 자동 더빙 · 자동 자막 한 번에",
                    style = TextStyle(fontSize = 12.sp, color = Color(0xCCFFFFFF))
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: CreditProduct,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFF0A84FF) else Color(0x1FFFFFFF)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2C2C2E))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected = selected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.title,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    )
                    if (product.highlight) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF0A84FF))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "BEST",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                )
                            )
                        }
                    }
                }
                product.subtitle?.let { sub ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = TextStyle(fontSize = 12.sp, color = Color(0x99EBEBF5)),
                    )
                }
            }
            Text(
                text = product.priceLabel,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val outerColor = if (selected) Color(0xFF0A84FF) else Color(0x66EBEBF5)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(2.dp, outerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF))
            )
        }
    }
}

@Composable
private fun PurchaseCta(
    productLabel: String?,
    isPurchasing: Boolean,
    onClick: () -> Unit,
) {
    val enabled = productLabel != null && !isPurchasing
    val bg = if (enabled) Color.White else Color(0x33FFFFFF)
    val fg = if (enabled) Color.Black else Color(0x66FFFFFF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPurchasing) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = productLabel?.let { "$it 구매" } ?: "상품을 선택하세요",
                style = TextStyle(
                    fontSize = 17.sp,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RestoreRow(isWorking: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isWorking) {
            CircularProgressIndicator(
                color = Color(0xFF0A84FF),
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "구매 내역 복원",
            style = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF0A84FF),
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp, horizontal = 8.dp)
        )
    }
}

/**
 * App Store 가이드라인 3.1.2 / 5.1.1 결제 고지문. TOS / 개인정보 링크는 실제 URL 핸들러가
 * 도입되면 추가 — 빈 onClick 으로 무동작 underlined link 를 보이게 두면 심사에서 reject.
 */
@Composable
private fun FinePrint() {
    Text(
        text = "결제는 구매 확인 시 Apple ID 또는 Google 계정으로 청구됩니다. " +
            "크레딧은 환불되지 않으며 미사용분도 양도/현금 환산할 수 없습니다.",
        style = TextStyle(
            fontSize = 11.sp,
            color = Color(0x99EBEBF5),
            lineHeight = 15.sp,
        )
    )
}
