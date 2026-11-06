package com.accountbook.product.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductApi {

    Optional<ProductView> find(long id);

    /** @throws com.accountbook.common.web.ApiException NOT_FOUND when absent */
    ProductView require(long id);

    Map<Long, ProductView> findAll(Collection<Long> ids);

    /** Returns the existing product with the same normalized name, brand and spec, otherwise creates one. */
    ProductView findOrCreate(ProductDraft draft);

    /** Products whose name matches (equal / contains / contained in) the given name, best match first. */
    List<ProductView> match(String name, int limit);
}
