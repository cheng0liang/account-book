package com.accountbook.ocr.internal;

import com.accountbook.ocr.api.OcrItem;
import com.accountbook.ocr.api.OcrResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tolerant conversion of the JSON an LLM returns (possibly fenced, numbers possibly as strings) into an {@link OcrResult}. */
final class ReceiptJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter[] DATE_TIMES = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm[:ss]")};

    private ReceiptJsonParser() {
    }

    static OcrResult parse(String raw, String provider, String defaultCurrency) throws Exception {
        JsonNode root = MAPPER.readTree(stripFence(raw));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        List<String> warnings = new ArrayList<>();
        JsonNode merchant = root.path("merchant");
        String merchantName = text(merchant.isObject() ? merchant.get("name") : root.get("merchant"));
        if (merchantName == null) {
            warnings.add("merchant_missing");
        }
        LocalDateTime purchasedAt = dateTime(text(root.get("purchasedAt")), warnings);
        String currency = text(root.get("currency"));
        currency = currency == null ? defaultCurrency : currency.toUpperCase(Locale.ROOT);

        List<OcrItem> items = new ArrayList<>();
        for (JsonNode n : root.path("items")) {
            String name = text(n.get("name"));
            if (name == null) {
                continue;
            }
            items.add(new OcrItem(name, text(n.get("brand")), text(n.get("specText")), number(n.get("quantity")),
                    number(n.get("unitPrice")), number(n.get("discount")), number(n.get("amount")),
                    text(n.get("category"))));
        }
        if (items.isEmpty()) {
            warnings.add("no_items");
        }
        return new OcrResult(provider, warnings, merchantName, upper(text(merchant.get("country"))),
                text(merchant.get("city")), text(merchant.get("district")), text(merchant.get("address")),
                purchasedAt, currency, number(root.get("totalAmount")), number(root.get("taxAmount")),
                number(root.get("discountAmount")), upper(text(root.get("paymentMethod"))), items);
    }

    static String stripFence(String s) {
        String t = s == null ? "" : s.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl < 0 ? "" : t.substring(nl + 1);
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        return start >= 0 && end > start ? t.substring(start, end + 1) : t.strip();
    }

    private static LocalDateTime dateTime(String s, List<String> warnings) {
        if (s == null) {
            warnings.add("date_missing");
            return null;
        }
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        try {
            warnings.add("time_missing");
            return LocalDate.parse(s.replace('/', '-')).atTime(12, 0);
        } catch (DateTimeParseException e) {
            warnings.remove("time_missing");
            warnings.add("date_missing");
            return null;
        }
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || n.isContainerNode()) {
            return null;
        }
        String s = n.asText().strip();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal number(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        String s = text(n);
        if (s == null) {
            return null;
        }
        String cleaned = Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("[^0-9.\\-]", "");
        try {
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
