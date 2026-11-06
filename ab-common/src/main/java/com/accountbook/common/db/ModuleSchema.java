package com.accountbook.common.db;

/**
 * Declares the Flyway scripts a module owns. Each module gets its own history table
 * ({@code flyway_<module>}) so it can be moved to another database without rewriting history.
 */
public record ModuleSchema(String module, String location) {

    public static ModuleSchema of(String module) {
        return new ModuleSchema(module, "classpath:db/migration/" + module);
    }
}
