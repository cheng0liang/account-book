package com.accountbook.receipt.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;

import com.accountbook.common.money.Money;
import com.accountbook.common.web.ApiException;
import com.accountbook.common.web.PageResult;
import com.accountbook.family.api.FamilyApi;
import com.accountbook.family.api.Scope;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.ReceiptApi;
import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.event.ReceiptDeletedEvent;
import com.accountbook.receipt.api.event.ReceiptSavedEvent;
import com.accountbook.user.api.UserApi;
import com.accountbook.user.api.UserView;
import com.accountbook.user.api.event.UserDeletedEvent;
import com.accountbook.user.api.event.UserRegisteredEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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
    private final FamilyApi families;
    private final UserApi users;

    ReceiptService(ReceiptRepository repo, ReceiptResolver resolver, MerchantApi merchants, ProductApi products,
                   ImageStore images, ApplicationEventPublisher events, FamilyApi families, UserApi users) {
        this.repo = repo;
        this.resolver = resolver;
        this.merchants = merchants;
        this.products = products;
        this.images = images;
        this.events = events;
        this.families = families;
        this.users = users;
    }

    /** Claims receipts saved before login existed for the very first registered user, in their registration tx. */
    @EventListener
    void on(UserRegisteredEvent e) {
        if (e.first()) {
            repo.claimLegacy(e.userId());
        }
    }

    /** Account deletion: the user's receipts (and their images) go with them. */
    @EventListener
    void on(UserDeletedEvent e) {
        for (var r : repo.briefsOf(e.userId())) {
            repo.delete(r.id());
            events.publishEvent(new ReceiptDeletedEvent(r.id()));
            if (r.imagePath() != null) {
                afterCommit(() -> images.delete(r.imagePath()));
            }
        }
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
    ReceiptForm create(long userId, ReceiptForm form) {
        var r = resolver.resolve(form);
        long id = repo.insert(toNew(userId, r, r.imageToken()), System.currentTimeMillis());
        return persistItemsAndPublish(userId, id, r);
    }

    @Transactional
    ReceiptForm update(long userId, long id, ReceiptForm form) {
        var existing = ownedBy(userId, id);
        var r = resolver.resolve(form);
        String image = r.imageToken() != null ? r.imageToken() : existing.imagePath();
        repo.update(id, toNew(existing.userId(), r, image), System.currentTimeMillis());
        if (existing.imagePath() != null && !existing.imagePath().equals(image)) {
            afterCommit(() -> images.delete(existing.imagePath()));
        }
        return persistItemsAndPublish(userId, id, r);
    }

    @Transactional
    void delete(long userId, long id) {
        var existing = ownedBy(userId, id);
        repo.delete(id);
        events.publishEvent(new ReceiptDeletedEvent(id));
        if (existing.imagePath() != null) {
            afterCommit(() -> images.delete(existing.imagePath()));
        }
    }

    ReceiptForm get(long userId, long id) {
        return toForm(userId, visibleTo(userId, id));
    }

    private ReceiptForm toForm(long userId, ReceiptRepository.Row row) {
        long id = row.id();
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
                row.imagePath() == null ? null : "/api/receipts/" + id + "/image", itemForms,
                owners(userId, List.of(row.userId())).get(row.userId()));
    }

    PageResult<ReceiptSummary> list(long userId, Scope scope, String from, String to, Long merchantId, String q,
                                    int page, int size) {
        int limit = PageResult.clampSize(size);
        var filter = new ReceiptRepository.Filter(scope.userIds(families, userId), blankToNull(from), blankToNull(to),
                merchantId, like(q));
        var rows = repo.search(filter, Math.max(page, 0) * limit, limit);
        var names = repo.firstItemNames(rows.stream().map(ReceiptRepository.Row::id).toList(), 3);
        var owners = owners(userId, rows.stream().map(ReceiptRepository.Row::userId).distinct().toList());
        var summaries = rows.stream().map(r -> new ReceiptSummary(r.id(), r.merchantId(), r.merchantName(),
                r.purchasedAt(), r.currency(), Money.fromMinor(r.totalMinor(), r.currency()), r.itemCount(),
                r.paymentMethod(), r.imagePath() != null, names.getOrDefault(r.id(), List.of()),
                owners.get(r.userId())))
                .collect(Collectors.toList());
        return new PageResult<>(summaries, repo.count(filter));
    }

    Optional<java.nio.file.Path> imageOf(long userId, long id) {
        return Optional.ofNullable(visibleTo(userId, id).imagePath()).flatMap(images::resolve);
    }

    /** The receipt if the caller owns it or shares a family with its owner; otherwise it does not exist for them. */
    private ReceiptRepository.Row visibleTo(long userId, long id) {
        var row = repo.findById(id).orElseThrow(() -> ApiException.notFound("receipt.notFound", id));
        if (row.userId() != userId && !families.memberUserIds(userId).contains(row.userId())) {
            throw ApiException.notFound("receipt.notFound", id);
        }
        return row;
    }

    private ReceiptRepository.Row ownedBy(long userId, long id) {
        var row = visibleTo(userId, id);
        if (row.userId() != userId) {
            throw ApiException.forbidden("receipt.ownerOnly");
        }
        return row;
    }

    private Map<Long, OwnerRef> owners(long viewerId, Collection<Long> ownerIds) {
        Map<Long, UserView> found = users.findAll(ownerIds);
        Map<Long, OwnerRef> out = new HashMap<>();
        for (long oid : ownerIds) {
            UserView u = found.get(oid);
            out.put(oid, new OwnerRef(oid, u == null ? null : u.nickname(), u == null ? null : u.avatarUrl(),
                    oid == viewerId));
        }
        return out;
    }

    private ReceiptForm persistItemsAndPublish(long userId, long id, ReceiptResolver.Resolved r) {
        repo.replaceItems(id, r.items().stream().map(it -> new ReceiptRepository.NewItem(it.product().id(),
                it.product().name(), it.quantity(), it.unitPriceMinor(), it.discountMinor(), it.amountMinor(),
                it.note())).toList());
        events.publishEvent(new ReceiptSavedEvent(repo.findFact(id).orElseThrow()));
        return get(userId, id);
    }

    private static ReceiptRepository.NewReceipt toNew(long userId, ReceiptResolver.Resolved r, String imagePath) {
        return new ReceiptRepository.NewReceipt(userId, r.merchant().id(), r.merchant().name(), r.purchasedAt(),
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
