package com.accountbook.receipt.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;
import static com.accountbook.common.text.NameNormalizer.normalize;

import com.accountbook.common.config.AppProperties;
import com.accountbook.common.web.ApiException;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.ocr.api.OcrApi;
import com.accountbook.ocr.api.OcrItem;
import com.accountbook.ocr.api.OcrResult;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductCategory;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Photo → stored image → OCR → editable draft pre-linked to existing merchants/products. Nothing is persisted but the image. */
@Service
class RecognitionService {

    private static final int CANDIDATES = 5;
    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    private final OcrApi ocr;
    private final ImageStore images;
    private final MerchantApi merchants;
    private final ProductApi products;
    private final AppProperties props;

    RecognitionService(OcrApi ocr, ImageStore images, MerchantApi merchants, ProductApi products, AppProperties props) {
        this.ocr = ocr;
        this.images = images;
        this.merchants = merchants;
        this.products = products;
        this.props = props;
    }

    RecognizeResponse recognize(byte[] bytes) {
        var type = ImageStore.detect(bytes)
                .orElseThrow(() -> ApiException.invalidField("image", "receipt.image.unsupported"));
        String token = images.save(bytes, type);
        OcrResult r = ocr.recognize(bytes, type.mime(), props.defaultCurrency());

        List<String> warnings = new ArrayList<>(r.warnings());
        var merchantMatches = r.merchantName() == null ? List.<MerchantView>of()
                : merchants.match(r.merchantName(), CANDIDATES);
        MerchantView exactMerchant = merchantMatches.stream()
                .filter(m -> normalize(m.name()).equals(normalize(r.merchantName()))).findFirst().orElse(null);
        if (r.merchantName() != null && exactMerchant == null) {
            warnings.add("merchant_not_matched");
        }

        List<ReceiptForm.ItemForm> items = new ArrayList<>();
        Map<Integer, List<ProductView>> candidates = new LinkedHashMap<>();
        for (OcrItem it : r.items()) {
            var matches = products.match(it.name(), CANDIDATES);
            ProductView exact = matches.stream().filter(p -> normalize(p.name()).equals(normalize(it.name())))
                    .filter(p -> specCompatible(p, it)).findFirst().orElse(null);
            var others = matches.stream().filter(p -> exact == null || p.id() != exact.id()).toList();
            if (!others.isEmpty()) {
                candidates.put(items.size(), others);
            }
            items.add(toItem(it, exact));
        }

        BigDecimal sum = items.stream().map(ReceiptForm.ItemForm::amount).filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (r.totalAmount() != null && !items.isEmpty() && r.totalAmount().compareTo(sum) != 0) {
            warnings.add("total_mismatch");
        }

        LocalDateTime at = r.purchasedAt() != null ? r.purchasedAt() : LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        String currency = r.currency() != null && com.accountbook.common.money.Money.isValidCurrency(r.currency())
                ? r.currency() : props.defaultCurrency();
        String payment = r.paymentMethod() != null && PaymentMethod.isValid(r.paymentMethod()) ? r.paymentMethod() : null;

        var draft = new ReceiptForm(null, toMerchant(r, exactMerchant), at.format(ReceiptResolver.STORED), currency,
                r.totalAmount(), r.taxAmount(), r.discountAmount(), payment, null, "OCR", token, null, items);
        return new RecognizeResponse(token, r.provider(), List.copyOf(warnings), draft, merchantMatches, candidates);
    }

    private static boolean specCompatible(ProductView p, OcrItem it) {
        String spec = blankToNull(it.specText());
        return spec == null || p.specText() == null || normalize(p.specText()).equals(normalize(spec));
    }

    private static ReceiptForm.MerchantRef toMerchant(OcrResult r, MerchantView exact) {
        if (exact != null) {
            return new ReceiptForm.MerchantRef(exact.id(), exact.name(), exact.country(), exact.city(),
                    exact.district(), exact.type(), exact.address(), exact.latitude(), exact.longitude());
        }
        String country = r.country() != null && COUNTRY.matcher(r.country()).matches()
                ? r.country().toUpperCase(Locale.ROOT) : null;
        return new ReceiptForm.MerchantRef(null, r.merchantName(), country, r.city(), r.district(), null,
                r.address(), null, null);
    }

    private static ReceiptForm.ItemForm toItem(OcrItem it, ProductView exact) {
        BigDecimal qty = it.quantity() != null && it.quantity().signum() > 0 ? it.quantity() : BigDecimal.ONE;
        BigDecimal unit = it.unitPrice();
        BigDecimal amount = it.amount();
        if (unit == null && amount != null) {
            unit = amount.add(it.discount() == null ? BigDecimal.ZERO : it.discount())
                    .divide(qty, 4, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (amount == null && unit != null) {
            amount = unit.multiply(qty).subtract(it.discount() == null ? BigDecimal.ZERO : it.discount());
        }
        String category = it.category() != null && ProductCategory.isValid(it.category()) ? it.category() : null;
        var product = exact != null
                ? new ReceiptForm.ProductRef(exact.id(), exact.name(), exact.brand(), exact.category(),
                        exact.specText(), exact.specValue(), exact.specUnit(), exact.barcode(), exact.tags())
                : new ReceiptForm.ProductRef(null, it.name(), it.brand(), category, it.specText(), null, null, null,
                        List.of());
        return new ReceiptForm.ItemForm(null, product, qty, unit, it.discount(), amount, null);
    }
}
