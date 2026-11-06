package com.accountbook.common.db;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class ModuleMigrator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ModuleMigrator.class);

    private final DataSource dataSource;
    private final List<ModuleSchema> schemas;

    public ModuleMigrator(DataSource dataSource, List<ModuleSchema> schemas) {
        this.dataSource = dataSource;
        this.schemas = schemas;
    }

    @Override
    public void afterPropertiesSet() {
        migrate(dataSource, schemas);
    }

    public static void migrate(DataSource dataSource, List<ModuleSchema> schemas) {
        for (ModuleSchema s : schemas) {
            var result = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(s.location())
                    .table("flyway_" + s.module())
                    // The schema is shared by all modules, so it is never empty for the 2nd+ module; baseline at 0
                    // so that module's V1 still runs.
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
            log.info("Schema [{}] at version {} ({} migrations applied)",
                    s.module(), result.targetSchemaVersion, result.migrationsExecuted);
        }
    }
}
