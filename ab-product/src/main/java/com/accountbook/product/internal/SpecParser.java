package com.accountbook.product.internal;

import com.accountbook.product.api.SpecUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/** Derives the structured spec (value/unit/dimension/base quantity) and a canonical identity key from user input. */
final class SpecParser {

    private static final Pattern TEXT = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l|pcs|公斤|千克|克|毫升|升|个|枚|入|本|只|粒|片|支|包|袋|瓶|罐|盒)$",
            Pattern.CASE_INSENSITIVE);

    record Spec(String text, String key, Double value, String unit, String dim, Double baseQty) {
        static final Spec NONE = new Spec(null, "", null, null, null, null);
    }

    private SpecParser() {
    }

    /** Returns null when exactly one of value/unit is supplied or the value is not positive. */
    static Spec derive(String specText, Double value, String unit) {
        boolean hasValue = value != null;
        boolean hasUnit = unit != null && !unit.isBlank();
        if (hasValue != hasUnit) {
            return null;
        }
        if (hasValue) {
            var u = SpecUnit.parse(unit);
            if (u.isEmpty() || value <= 0) {
                return null;
            }
            return numeric(blank(specText) ? null : specText.strip(), value, u.get());
        }
        if (blank(specText)) {
            return Spec.NONE;
        }
        String text = specText.strip();
        var m = TEXT.matcher(text);
        if (m.matches()) {
            var u = unitOf(m.group(2).toLowerCase(Locale.ROOT));
            if (u != null) {
                return numeric(text, Double.parseDouble(m.group(1)), u);
            }
        }
        return new Spec(text, com.accountbook.common.text.NameNormalizer.normalize(text), null, null, null, null);
    }

    private static Spec numeric(String text, double value, SpecUnit unit) {
        double base = unit.toBase(value);
        String canonical = text != null ? text : fmt(value) + unit.symbol();
        String key = unit.dimension().name() + ":" + fmt(base);
        return new Spec(canonical, key, value, unit.symbol(), unit.dimension().name(), base);
    }

    private static SpecUnit unitOf(String s) {
        return switch (s) {
            case "kg", "公斤", "千克" -> SpecUnit.KG;
            case "g", "克" -> SpecUnit.G;
            case "ml", "毫升" -> SpecUnit.ML;
            case "l", "升" -> SpecUnit.L;
            case "pcs", "个", "枚", "入", "本", "只", "粒", "片", "支", "包", "袋", "瓶", "罐", "盒" -> SpecUnit.PCS;
            default -> null;
        };
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(Math.round(v * 1e6) / 1e6);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
