package com.accountbook.receipt.api;

/** Amounts in the receipt currency's minor unit; {@code amountMinor} is net of the line discount. */
public record ReceiptItemFact(long productId, double quantity, long unitPriceMinor, long discountMinor, long amountMinor) {
}
