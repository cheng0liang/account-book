package com.accountbook.receipt.api.event;

/** Published (in the writer's transaction) after a receipt is deleted. */
public record ReceiptDeletedEvent(long receiptId) {
}
