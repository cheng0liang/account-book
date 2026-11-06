package com.accountbook.merchant.api;

import java.util.List;
import java.util.Optional;

public interface MerchantApi {

    Optional<MerchantView> find(long id);

    /** @throws com.accountbook.common.web.ApiException NOT_FOUND when absent */
    MerchantView require(long id);

    /** Returns the existing merchant with the same normalized name and city, otherwise creates one. */
    MerchantView findOrCreate(MerchantDraft draft);

    /** Merchants whose name matches (equal / contains / contained in) the given name, best match first. */
    List<MerchantView> match(String name, int limit);
}
