package com.accountbook.common.text;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical form used to match merchant/product names typed by hand or read by OCR. */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(n.length());
        n.codePoints().filter(Character::isLetterOrDigit).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
