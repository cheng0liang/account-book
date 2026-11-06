package com.accountbook.receipt.api;

import java.util.Optional;
import java.util.function.Consumer;

public interface ReceiptApi {

    Optional<ReceiptFact> findFact(long id);

    /** Streams every receipt in id order in bounded batches; used to rebuild downstream projections. */
    void forEachFact(Consumer<ReceiptFact> consumer);
}
