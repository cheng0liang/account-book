package com.accountbook.family.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FamilyModuleConfig {

    @Bean
    ModuleSchema familySchema() {
        return ModuleSchema.of("family");
    }

    @Bean
    MessageBundle familyMessages() {
        return new MessageBundle("i18n/family");
    }
}
