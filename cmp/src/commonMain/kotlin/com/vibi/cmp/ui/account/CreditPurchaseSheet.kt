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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.platform.PurchaseLauncher
import com.vibi.cmp.platform.PurchaseResult
import com.vibi.cmp.platform.RestoreResult
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.shared.domain.model.IapPlatform
import com.vibi.shared.ui.account.CreditProduct
import kotlinx.coroutines.launch

/**
 * 크레딧 구매 storefront — **App Store / Play Store 심사 통과 디자인**.
 *
 * 가이드라인 핵심:
 *  - Apple 시스템 결제 popup (Face ID / Touch ID prompt) 은 OS 만 그린다 (3.1.1 / 4.1 / 4.5).
 *  - 비소비성 / 구독이 아니어도 "구매 복원" 노출 권장 (3.1.1).
 *  - 결제 고지 ("Apple ID 청구 / 환불 불가") 표시 (3.1.2 / 5.1.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditPurchaseSheet(
    products: List<CreditProduct>,
    currentCredits: Int,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onPurchased: suspend (
        product: CreditProduct,
        platform: IapPlatform,
        receipt: String,
        transactionId: String,
    ) -> Result<Unit>,
    onAdminGrant: suspend (product: CreditProduct) -> Result<Unit>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val launcher = remember { PurchaseLauncher() }
    val scope = rememberCoroutineScope()
    val tokens = LocalVibiColors.current

    var selected by remember(products) {
        mutableStateOf(products.firstOrNull { it.highlight } ?: products.firstOrNull())
    }
    var status by remember { mutableStateOf<Status>(Status.Idle) }

    ModalBottomSheet(
        onDismissRequest = { if (status != Status.Purchasing) onDismiss() },
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(currentCredits = currentCredits, isAdmin = isAdmin)

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
                isAdmin = isAdmin,
                isPurchasing = status == Status.Purchasing,
                onClick = {
                    val target = selected ?: return@PurchaseCta
                    status = Status.Purchasing
                    scope.launch {
                        if (isAdmin) {
                            status = onAdminGrant(target).fold(
                                onSuccess = { Status.Success },
                                onFailure = { Status.Error(it.message ?: "Failed to grant credits") },
                            )
                            return@launch
                        }
                        when (val r = launcher.purchase(target.productId)) {
                            is PurchaseResult.Success -> {
                                // BFF 검증·가산이 성공해야 transaction 을 finish. 실패 시 unfinished
                                // queue 에 남겨두면 Transaction.updates listener 가 다음 실행에서 재시도.
                                val bff = onPurchased(target, r.platform, r.receipt, r.transactionId)
                                status = bff.fold(
                                    onSuccess = {
                                        launcher.finishTransaction(r.transactionId)
                                        Status.Success
                                    },
                                    onFailure = { Status.Deferred },
                                )
                            }
                            is PurchaseResult.UserCancelled -> status = Status.Idle
                            is PurchaseResult.Failed -> status = Status.Error(r.message)
                        }
                    }
                }
            )

            when (val s = status) {
                is Status.Error -> Text(
                    text = "⚠ ${s.message}",
                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.error),
                )
                Status.Success -> Text(
                    text = if (isAdmin) "✓ Admin credits applied." else "✓ Purchase complete.",
                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary),
                )
                Status.Deferred -> Text(
                    text = "Payment received. Credits will appear shortly.",
                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary),
                )
                else -> Unit
            }

            // admin 분기는 IAP 우회라 store 가이드라인 (3.1.1 복원, 3.1.2 청구 고지) 적용 대상 아님.
            if (!isAdmin) {
                RestoreRow(
                    isWorking = status == Status.Restoring,
                    onClick = {
                        if (status == Status.Restoring) return@RestoreRow
                        status = Status.Restoring
                        scope.launch {
                            when (val r = launcher.restorePurchases()) {
                                RestoreResult.Completed -> status = Status.Success
                                RestoreResult.UserCancelled -> status = Status.Idle
                                is RestoreResult.Failed -> status = Status.Error(r.message)
                            }
                        }
                    }
                )
                FinePrint()
            }
        }
    }

    LaunchedEffect(status) {
        if (status is Status.Success || status is Status.Deferred) {
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
    /** 결제는 성공했으나 BFF 가산이 일시 실패 — Transaction.updates listener 가 재시도. */
    data object Deferred : Status
    data class Error(val message: String) : Status
}

@Composable
private fun Header(currentCredits: Int, isAdmin: Boolean) {
    val tokens = LocalVibiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isAdmin) "Add credits" else "Buy credits",
                    style = TextStyle(
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                )
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "ADMIN",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Balance · $currentCredits credits (≈ $currentCredits min)",
                style = TextStyle(fontSize = 13.sp, color = tokens.mutedText),
            )
        }
    }
}

@Composable
private fun HeroBanner() {
    val tokens = LocalVibiColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(tokens.gradientLavender, tokens.gradientSky)
                )
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.panelBg.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    style = TextStyle(fontSize = 22.sp, color = tokens.onBackgroundPrimary)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "1 credit = 1 minute of separation",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = tokens.onBackgroundPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "1 minute of vocal or background separation",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = tokens.onBackgroundPrimary.copy(alpha = 0.75f),
                    )
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
    val tokens = LocalVibiColors.current
    val borderColor = if (selected) tokens.accent else tokens.hairline
    val borderWidth = if (selected) 2.dp else 1.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.chipBg)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        RadioDot(selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = product.title,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                )
                if (product.highlight) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tokens.accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "BEST",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
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
                    style = TextStyle(fontSize = 12.sp, color = tokens.mutedText),
                )
            }
        }
        Text(
            text = product.priceLabel,
            style = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val tokens = LocalVibiColors.current
    val outerColor = if (selected) tokens.accent else tokens.mutedText
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(2.dp, outerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tokens.accent)
            )
        }
    }
}

@Composable
private fun PurchaseCta(
    productLabel: String?,
    isAdmin: Boolean,
    isPurchasing: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val enabled = productLabel != null && !isPurchasing
    val bg = if (enabled) tokens.accent else tokens.chipBgDisabled
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else tokens.chipContentDisabled
    val label = when {
        productLabel == null -> "Choose a plan"
        isAdmin -> "Grant credits (admin)"
        else -> "Buy $productLabel"
    }
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
                color = fg,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
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
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "Restore purchases",
            style = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
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
    val tokens = LocalVibiColors.current
    Text(
        text = "1 credit = separation of 1 minute of vocals or background. " +
            "Payment is charged to your Apple ID or Google account at confirmation. " +
            "Credits are non-refundable and cannot be transferred or redeemed for cash.",
        style = TextStyle(
            fontSize = 11.sp,
            color = tokens.mutedText,
            lineHeight = 15.sp,
        )
    )
}
