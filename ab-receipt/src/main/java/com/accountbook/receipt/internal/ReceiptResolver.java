package com.accountbook.receipt.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;

import com.accountbook.common.money.Money;
import com.accountbook.common.web.ApiException;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantDraft;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductDraft;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Validates a {@link ReceiptForm}, resolves/creates its merchant and products, and converts amounts to minor units. */
@Component
class ReceiptResolver {

    static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter[] ACCEPTED = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")};

    record Resolved(MerchantView merchant, String purchasedAt, String purchasedDate, String currency, long totalMinor,
                    Long taxMinor, long discountMinor, String paymentMethod, String note, String source,
                    String imageToken, List<ResolvedItem> items) {
    }

    record ResolvedItem(ProductView product, double quantity, long unitPriceMinor, long discountMinor,
                        long amountMinor, String note) {
    }

    private final MerchantApi merchants;
    private final ProductApi products;
    private final ImageStore images;

    ReceiptResolver(MerchantApi merchants, ProductApi products, ImageStore images) {
        this.merchants = merchants;
        this.products = products;
        this.images = images;
    }

    Resolved resolve(ReceiptForm f) {
        if (f.merchant() == null) {
            throw ApiException.invalidField("merchant.name", "validation.required");
        }
        String currency = f.currency() == null ? "" : f.currency().strip().toUpperCase(Locale.ROOT);
        if (!Money.isValidCurrency(currency)) {
            throw ApiException.invalidField("currency", "validation.currency");
        }
        LocalDateTime at = parseDateTime(f.purchasedAt());
        if (f.items() == null || f.items().isEmpty()) {
            throw ApiException.invalidField("items", "receipt.items.required");
        }
        String payment = blankToNull(f.paymentMethod());
        if (payment != null && !PaymentMethod.isValid(payment)) {
            throw ApiException.invalidField("paymentMethod", "validation.range");
        }
        String source = blankToNull(f.source()) == null ? "MANUAL" : f.source().strip();
        if (!source.equals("OCR") && !source.equals("MANUAL")) {
            throw ApiException.invalidField("source", "validation.range");
        }
        String token = blankToNull(f.imageToken());
        if (token != null && images.resolve(token).isEmpty()) {
            throw ApiException.invalidField("imageToken", "receipt.image.invalid");
        }

        var merchant = resolveMerchant(f.merchant());
        List<ResolvedItem> items = new ArrayList<>();
        long sum = 0;
        for (int i = 0; i < f.items().size(); i++) {
            var item = resolveItem(f.items().get(i), i, currency);
            items.add(item);
            sum += item.amountMinor();
        }
        long total = f.totalAmount() != null ? minor(f.totalAmount(), "totalAmount", currency, true) : sum;
        Long tax = f.taxAmount() == null ? null : minor(f.taxAmount(), "taxAmount", currency, true);
        long discount = f.discountAmount() == null ? 0 : minor(f.discountAmount(), "discountAmount", currency, true);
        return new Resolved(merchant, at.format(STORED), at.toLocalDate().toString(), currency, total, tax, discount,
                payment, blankToNull(f.note()), source, token, items);
    }

    private MerchantView resolveMerchant(ReceiptForm.MerchantRef m) {
        if (m.id() != null) {
            return merchants.require(m.id());
        }
        if (blankToNull(m.name()) == null) {
            throw ApiException.invalidField("merchant.name", "validation.required");
        }
        return merchants.findOrCreate(new MerchantDraft(m.name(), m.country(), m.city(), m.district(), m.type(),
                m.address(), m.latitude(), m.longitude()));
    }

    private ResolvedItem resolveItem(ReceiptForm.ItemForm it, int index, String currency) {
        String path = "items[" + index + "]";
        if (it == null || it.product() == null) {
            throw ApiException.invalidField(path + ".product.name", "validation.required");
        }
        if (it.quantity() == null || it.quantity().signum() <= 0) {
            throw ApiException.invalidField(path + ".quantity", "validation.positive");
        }
        if (it.unitPrice() == null) {
            throw ApiException.invalidField(path + ".unitPrice", "validation.required");
        }
        long unit = minor(it.unitPrice(), path + ".unitPrice", currency, true);
        long discount = it.discount() == null ? 0 : minor(it.discount(), path + ".discount", currency, true);
        double qty = it.quantity().doubleValue();
        long amount = it.amount() != null ? minor(it.amount(), path + ".amount", currency, false)
                : Math.round(qty * unit) - discount;
        var p = it.product();
        ProductView product;
        if (p.id() != null) {
            product = products.require(p.id());
        } else {
            if (blankToNull(p.name()) == null) {
                throw ApiException.invalidField(path + ".product.name", "validation.required");
            }
            product = products.findOrCreate(new ProductDraft(p.name(), p.brand(), p.category(), p.specText(),
                    p.specValue(), p.specUnit(), p.barcode(), p.tags()));
        }
        return new ResolvedItem(product, qty, unit, discount, amount, blankToNull(it.note()));
    }

    private static long minor(BigDecimal v, String field, String currency, boolean nonNegative) {
        try {
            long minor = Money.toMinor(v, currency);
            if (nonNegative && minor < 0) {
                throw ApiException.invalidField(field, "validation.nonNegative");
            }
            return minor;
        } catch (ArithmeticException e) {
            throw ApiException.invalidField(field, "validation.range");
        }
    }

    static LocalDateTime parseDateTime(String s) {
        if (s != null) {
            for (DateTimeFormatter f : ACCEPTED) {
                try {
                    return LocalDateTime.parse(s.strip(), f);
                } catch (DateTimeParseException ignored) {
                    // try next format
                }
            }
        }
        throw ApiException.invalidField("purchasedAt", "validation.range");
    }
}
