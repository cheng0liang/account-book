package com.accountbook.receipt.internal;

import com.accountbook.merchant.api.MerchantView;
import com.accountbook.product.api.ProductView;
import java.util.List;
import java.util.Map;

record RecognizeResponse(
        String imageToken,
        String provider,
        List<String> warnings,
        ReceiptForm draft,
        List<MerchantView> merchantCandidates,
        Map<Integer, List<ProductView>> productCandidates) {
}
