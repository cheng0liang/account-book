package com.accountbook.product.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ProductModuleConfig {

    @Bean
    ModuleSchema productSchema() {
        return ModuleSchema.of("product");
    }

    @Bean
    MessageBundle productMessages() {
        return new MessageBundle("i18n/product");
    }
}
