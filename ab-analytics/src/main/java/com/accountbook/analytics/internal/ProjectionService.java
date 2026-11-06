package com.accountbook.analytics.internal;

import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.event.MerchantChangedEvent;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.event.ProductChangedEvent;
import com.accountbook.receipt.api.ReceiptApi;
import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.ReceiptItemFact;
import com.accountbook.receipt.api.event.ReceiptDeletedEvent;
import com.accountbook.receipt.api.event.ReceiptSavedEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the read-side projections in step with receipts. Event handlers run in the writer's transaction, so a
 * receipt and its projection commit (or roll back) together. Handlers are idempotent per receipt id.
 */
@Component
class ProjectionService {

    /** Bump when projection logic changes; a mismatch triggers a full rebuild at startup. */
    static final long REVISION = 1;

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final ProjectionRepository repo;
    private final MerchantApi merchants;
    private final ProductApi products;
    private final ReceiptApi receipts;
    private final TransactionTemplate tx;

    ProjectionService(ProjectionRepository repo, MerchantApi merchants, ProductApi products, ReceiptApi receipts,
                      TransactionTemplate tx) {
        this.repo = repo;
        this.merchants = merchants;
        this.products = products;
        this.receipts = receipts;
        this.tx = tx;
    }

    @EventListener
    @Transactional
    public void on(ReceiptSavedEvent e) {
        ReceiptFact f = e.receipt();
        Set<ProjectionRepository.Bucket> buckets = new LinkedHashSet<>(repo.bucketsOfReceipt(f.id()));
        repo.deleteFacts(f.id());
        syncDimensions(Set.of(f.merchantId()), f.items().stream().map(ReceiptItemFact::productId).toList());
        repo.insertFacts(f);
        for (ReceiptItemFact it : f.items()) {
            buckets.add(new ProjectionRepository.Bucket(f.purchasedDate(), f.currency(), f.merchantId(), it.productId()));
        }
        repo.rebuildBuckets(buckets);
        repo.bumpVersion();
    }

    @EventListener
    @Transactional
    public void on(ReceiptDeletedEvent e) {
        var buckets = repo.bucketsOfReceipt(e.receiptId());
        repo.deleteFacts(e.receiptId());
        repo.rebuildBuckets(buckets);
        repo.bumpVersion();
    }

    @EventListener
    @Transactional
    public void on(MerchantChangedEvent e) {
        repo.upsertMerchant(e.merchant());
        repo.bumpVersion();
    }

    @EventListener
    @Transactional
    public void on(ProductChangedEvent e) {
        repo.upsertProduct(e.product());
        repo.bumpVersion();
    }

    @EventListener(ApplicationReadyEvent.class)
    void rebuildIfStale() {
        if (repo.revision() != REVISION) {
            rebuildAll();
        }
    }

    void rebuildAll() {
        tx.executeWithoutResult(status -> {
            repo.clearAll();
            Set<Long> merchantIds = new HashSet<>();
            Set<Long> productIds = new HashSet<>();
            int[] count = {0};
            receipts.forEachFact(f -> {
                repo.insertFacts(f);
                merchantIds.add(f.merchantId());
                f.items().forEach(it -> productIds.add(it.productId()));
                count[0]++;
            });
            syncDimensions(merchantIds, productIds);
            repo.rebuildAllDaily();
            repo.setRevision(REVISION);
            repo.bumpVersion();
            log.info("Analytics projection rebuilt from {} receipts", count[0]);
        });
    }

    private void syncDimensions(Collection<Long> merchantIds, Collection<Long> productIds) {
        merchantIds.forEach(id -> merchants.find(id).ifPresent(repo::upsertMerchant));
        if (!productIds.isEmpty()) {
            products.findAll(new HashSet<>(productIds)).values().forEach(repo::upsertProduct);
        }
    }
}
