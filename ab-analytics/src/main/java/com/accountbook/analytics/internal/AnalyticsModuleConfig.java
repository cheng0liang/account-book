package com.accountbook.analytics.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AnalyticsModuleConfig {

    @Bean
    ModuleSchema analyticsSchema() {
        return ModuleSchema.of("analytics");
    }

    @Bean
    MessageBundle analyticsMessages() {
        return new MessageBundle("i18n/analytics");
    }
}
