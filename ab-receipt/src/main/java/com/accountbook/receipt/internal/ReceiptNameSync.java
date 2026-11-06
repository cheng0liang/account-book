package com.accountbook.receipt.internal;

import com.accountbook.merchant.api.event.MerchantChangedEvent;
import com.accountbook.product.api.event.ProductChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps the denormalised merchant/product names on receipts (used for list display) in step with renames. */
@Component
class ReceiptNameSync {

    private final ReceiptRepository repo;

    ReceiptNameSync(ReceiptRepository repo) {
        this.repo = repo;
    }

    @EventListener
    void on(MerchantChangedEvent e) {
        repo.renameMerchant(e.merchant().id(), e.merchant().name());
    }

    @EventListener
    void on(ProductChangedEvent e) {
        repo.renameProduct(e.product().id(), e.product().name());
    }
}
