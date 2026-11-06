package com.accountbook.merchant.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MerchantModuleConfig {

    @Bean
    ModuleSchema merchantSchema() {
        return ModuleSchema.of("merchant");
    }

    @Bean
    MessageBundle merchantMessages() {
        return new MessageBundle("i18n/merchant");
    }
}
