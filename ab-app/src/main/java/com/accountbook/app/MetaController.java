package com.accountbook.app;

import com.accountbook.common.config.AppProperties;
import com.accountbook.merchant.api.MerchantType;
import com.accountbook.ocr.api.OcrApi;
import com.accountbook.product.api.ProductCategory;
import com.accountbook.product.api.SpecUnit;
import com.accountbook.receipt.api.PaymentMethod;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Static enumerations the SPA needs to render pickers; kept here because it spans every module's api. */
@RestController
@RequestMapping("/api/meta")
class MetaController {

    private final Map<String, Object> body;

    MetaController(AppProperties props, OcrApi ocr, @Value("${ab.version:0.1.0}") String version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", version);
        m.put("defaultCurrency", props.defaultCurrency());
        m.put("ocrProvider", ocr.providerName());
        m.put("currencies", props.currencies());
        m.put("merchantTypes", names(MerchantType.values()));
        m.put("productCategories", names(ProductCategory.values()));
        m.put("paymentMethods", names(PaymentMethod.values()));
        m.put("specUnits", Arrays.stream(SpecUnit.values()).map(SpecUnit::symbol).toList());
        this.body = Map.copyOf(m);
    }

    @GetMapping
    Map<String, Object> meta() {
        return body;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
