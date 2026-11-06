package com.accountbook.product.api.event;

import com.accountbook.product.api.ProductView;

/** Published (in the writer's transaction) whenever a product is created or updated, tags included. */
public record ProductChangedEvent(ProductView product) {
}
