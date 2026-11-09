package com.accountbook.user.internal;

import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.i18n.MessageBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UserModuleConfig {

    @Bean
    ModuleSchema userSchema() {
        return ModuleSchema.of("user");
    }

    @Bean
    MessageBundle userMessages() {
        return new MessageBundle("i18n/user");
    }
}
