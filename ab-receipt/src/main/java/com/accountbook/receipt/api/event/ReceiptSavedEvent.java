package com.accountbook.receipt.api.event;

import com.accountbook.receipt.api.ReceiptFact;

/** Published (in the writer's transaction) after a receipt is created or replaced. */
public record ReceiptSavedEvent(ReceiptFact receipt) {
}
