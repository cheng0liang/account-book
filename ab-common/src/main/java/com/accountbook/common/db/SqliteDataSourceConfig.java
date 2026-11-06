package com.accountbook.common.db;

import com.accountbook.common.config.AppProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SqliteDataSourceConfig {

    /**
     * WAL + IMMEDIATE write transactions: readers never block on the writer, and a write
     * transaction takes the lock up front so it can't fail midway with SQLITE_BUSY.
     * Consequence: do not use readOnly transactions; plain reads run in autocommit.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(AppProperties props) {
        Path file = props.dbFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data dir " + file.getParent(), e);
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("ab-sqlite");
        cfg.setJdbcUrl(url(file, props.db().busyTimeoutMs()));
        cfg.setMaximumPoolSize(props.db().poolSize());
        return new HikariDataSource(cfg);
    }

    public static String url(Path file, int busyTimeoutMs) {
        return "jdbc:sqlite:" + file
                + "?journal_mode=WAL&synchronous=NORMAL&foreign_keys=true"
                + "&transaction_mode=IMMEDIATE&busy_timeout=" + busyTimeoutMs;
    }
}
