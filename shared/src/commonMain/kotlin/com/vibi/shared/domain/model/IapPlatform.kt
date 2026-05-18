package com.vibi.shared.domain.model

/**
 * IAP 결제 시스템 식별자. BFF `credit_transactions.platform` 컬럼의 CHECK constraint
 * (`V5__user_credits_and_account_delete.sql`) 와 1:1.
 */
enum class IapPlatform(val wireName: String) {
    APPLE("apple"),
    GOOGLE("google");

    companion object {
        fun fromWire(wire: String): IapPlatform? =
            entries.firstOrNull { it.wireName == wire.lowercase().trim() }
    }
}
