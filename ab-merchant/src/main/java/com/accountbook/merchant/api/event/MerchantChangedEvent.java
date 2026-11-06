package com.accountbook.merchant.api.event;

import com.accountbook.merchant.api.MerchantView;

/** Published (in the writer's transaction) whenever a merchant is created or updated. */
public record MerchantChangedEvent(MerchantView merchant) {
}
