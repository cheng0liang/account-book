package com.accountbook.receipt.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;

import com.accountbook.common.money.Money;
import com.accountbook.common.web.ApiException;
import com.accountbook.common.web.PageResult;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.ReceiptApi;
import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.event.ReceiptDeletedEvent;
import com.accountbook.receipt.api.event.ReceiptSavedEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class ReceiptService implements ReceiptApi {

    private final ReceiptRepository repo;
    private final ReceiptResolver resolver;
    private final MerchantApi merchants;
    private final ProductApi products;
    private final ImageStore images;
    private final ApplicationEventPublisher events;

    ReceiptService(ReceiptRepository repo, ReceiptResolver resolver, MerchantApi merchants, ProductApi products,
                   ImageStore images, ApplicationEventPublisher events) {
        this.repo = repo;
        this.resolver = resolver;
        this.merchants = merchants;
        this.products = products;
        this.images = images;
        this.events = events;
    }

    @Override
    public Optional<ReceiptFact> findFact(long id) {
        return repo.findFact(id);
    }

    @Override
    public void forEachFact(Consumer<ReceiptFact> consumer) {
        repo.forEachFact(consumer);
    }

    @Transactional
    ReceiptForm create(ReceiptForm form) {
        var r = resolver.resolve(form);
        long id = repo.insert(toNew(r, r.imageToken()), System.currentTimeMillis());
        return persistItemsAndPublish(id, r);
    }

    @Transactional
    ReceiptForm update(long id, ReceiptForm form) {
        var existing = repo.findById(id).orElseThrow(() -> ApiException.notFound("receipt.notFound", id));
        var r = resolver.resolve(form);
        String image = r.imageToken() != null ? r.imageToken() : existing.imagePath();
        repo.update(id, toNew(r, image), System.currentTimeMillis());
        if (existing.imagePath() != null && !existing.imagePath().equals(image)) {
            afterCommit(() -> images.delete(existing.imagePath()));
        }
        return persistItemsAndPublish(id, r);
    }

    @Transactional
    void delete(long id) {
        var existing = repo.findById(id).orElseThrow(() -> ApiException.notFound("receipt.notFound", id));
        repo.delete(id);
        events.publishEvent(new ReceiptDeletedEvent(id));
        if (existing.imagePath() != null) {
            afterCommit(() -> images.delete(existing.imagePath()));
        }
    }

    ReceiptForm get(long id) {
        var row = repo.findById(id).orElseThrow(() -> ApiException.notFound("receipt.notFound", id));
        var items = repo.items(id);
        MerchantView m = merchants.find(row.merchantId()).orElse(null);
        Map<Long, ProductView> prods = products.findAll(items.stream().map(ReceiptRepository.ItemRow::productId).toList());
        String cur = row.currency();
        var merchantRef = m != null
                ? new ReceiptForm.MerchantRef(m.id(), m.name(), m.country(), m.city(), m.district(), m.type(),
                        m.address(), m.latitude(), m.longitude())
                : new ReceiptForm.MerchantRef(row.merchantId(), row.merchantName(), null, null, null, null, null, null, null);
        var itemForms = items.stream().map(it -> {
            ProductView p = prods.get(it.productId());
            var ref = p != null
                    ? new ReceiptForm.ProductRef(p.id(), p.name(), p.brand(), p.category(), p.specText(), p.specValue(),
                            p.specUnit(), p.barcode(), p.tags())
                    : new ReceiptForm.ProductRef(it.productId(), it.productName(), null, null, null, null, null, null, List.of());
            return new ReceiptForm.ItemForm(it.id(), ref, java.math.BigDecimal.valueOf(it.quantity()).stripTrailingZeros(),
                    Money.fromMinor(it.unitPriceMinor(), cur), Money.fromMinor(it.discountMinor(), cur),
                    Money.fromMinor(it.amountMinor(), cur), it.note());
        }).toList();
        return new ReceiptForm(id, merchantRef, row.purchasedAt(), cur, Money.fromMinor(row.totalMinor(), cur),
                row.taxMinor() == null ? null : Money.fromMinor(row.taxMinor(), cur),
                Money.fromMinor(row.discountMinor(), cur), row.paymentMethod(), row.note(), row.source(), null,
                row.imagePath() == null ? null : "/api/receipts/" + id + "/image", itemForms);
    }

    PageResult<ReceiptSummary> list(String from, String to, Long merchantId, String q, int page, int size) {
        int limit = PageResult.clampSize(size);
        var filter = new ReceiptRepository.Filter(blankToNull(from), blankToNull(to), merchantId, like(q));
        var rows = repo.search(filter, Math.max(page, 0) * limit, limit);
        var names = repo.firstItemNames(rows.stream().map(ReceiptRepository.Row::id).toList(), 3);
        var summaries = rows.stream().map(r -> new ReceiptSummary(r.id(), r.merchantId(), r.merchantName(),
                r.purchasedAt(), r.currency(), Money.fromMinor(r.totalMinor(), r.currency()), r.itemCount(),
                r.paymentMethod(), r.imagePath() != null, names.getOrDefault(r.id(), List.of())))
                .collect(Collectors.toList());
        return new PageResult<>(summaries, repo.count(filter));
    }

    Optional<java.nio.file.Path> imageOf(long id) {
        return repo.findById(id).map(ReceiptRepository.Row::imagePath).flatMap(images::resolve);
    }

    private ReceiptForm persistItemsAndPublish(long id, ReceiptResolver.Resolved r) {
        repo.replaceItems(id, r.items().stream().map(it -> new ReceiptRepository.NewItem(it.product().id(),
                it.product().name(), it.quantity(), it.unitPriceMinor(), it.discountMinor(), it.amountMinor(),
                it.note())).toList());
        events.publishEvent(new ReceiptSavedEvent(repo.findFact(id).orElseThrow()));
        return get(id);
    }

    private static ReceiptRepository.NewReceipt toNew(ReceiptResolver.Resolved r, String imagePath) {
        return new ReceiptRepository.NewReceipt(r.merchant().id(), r.merchant().name(), r.purchasedAt(),
                r.purchasedDate(), r.currency(), r.totalMinor(), r.taxMinor(), r.discountMinor(), r.paymentMethod(),
                r.note(), r.source(), imagePath, r.items().size());
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String like(String q) {
        String s = blankToNull(q);
        return s == null ? null : "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
