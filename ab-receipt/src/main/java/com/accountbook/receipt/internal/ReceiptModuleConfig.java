package com.accountbook.receipt.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReceiptModuleConfig {

    @Bean
    ModuleSchema receiptSchema() {
        return ModuleSchema.of("receipt");
    }

    @Bean
    MessageBundle receiptMessages() {
        return new MessageBundle("i18n/receipt");
    }
}
