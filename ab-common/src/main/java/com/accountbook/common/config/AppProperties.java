package com.accountbook.common.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ab")
public record AppProperties(
        @DefaultValue("./data") String dataDir,
        @DefaultValue("JPY") String defaultCurrency,
        @DefaultValue({"JPY", "CNY", "TWD", "USD", "EUR"}) List<String> currencies,
        @DefaultValue Db db) {

    public record Db(@DefaultValue("8") int poolSize, @DefaultValue("5000") int busyTimeoutMs) {
    }

    public Path dbFile() {
        return Path.of(dataDir, "account-book.db").toAbsolutePath();
    }
}
