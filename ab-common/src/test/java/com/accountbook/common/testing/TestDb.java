package com.accountbook.common.testing;

import com.accountbook.common.db.ModuleMigrator;
import com.accountbook.common.db.ModuleSchema;
import com.accountbook.common.db.SqliteDataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQLite (WAL) in a temp dir with the given modules migrated; modules use it for service-level tests. */
public final class TestDb implements AutoCloseable {

    private final Path dir;
    private final HikariDataSource dataSource;

    private TestDb(Path dir, HikariDataSource dataSource) {
        this.dir = dir;
        this.dataSource = dataSource;
    }

    public static TestDb create(String... modules) {
        try {
            Path dir = Files.createTempDirectory("ab-test-");
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(SqliteDataSourceConfig.url(dir.resolve("test.db"), 5000));
            cfg.setMaximumPoolSize(4);
            var ds = new HikariDataSource(cfg);
            ModuleMigrator.migrate(ds, List.of(modules).stream().map(ModuleSchema::of).toList());
            return new TestDb(dir, ds);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }

    @Override
    public void close() {
        dataSource.close();
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp dir cleanup is best effort
        }
    }
}
