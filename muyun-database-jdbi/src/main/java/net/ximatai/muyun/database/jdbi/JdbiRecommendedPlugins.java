package net.ximatai.muyun.database.jdbi;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

public final class JdbiRecommendedPlugins {

    private JdbiRecommendedPlugins() {
    }

    /**
     * Install plugins for generic usage (MySQL/PostgreSQL shared path).
     */
    public static Jdbi installCommon(Jdbi jdbi) {
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.installPlugin(new Jackson2Plugin());
        return jdbi;
    }

    /**
     * Install only SqlObject plugin for lightweight DAO usage.
     */
    public static Jdbi installSqlObject(Jdbi jdbi) {
        jdbi.installPlugin(new SqlObjectPlugin());
        return jdbi;
    }

    public static Jdbi installPostgres(Jdbi jdbi) {
        jdbi.installPlugin(newPlugin("org.jdbi.v3.postgres.PostgresPlugin"));
        return jdbi;
    }

    private static JdbiPlugin newPlugin(String pluginClassName) {
        try {
            Class<?> pluginClass = Class.forName(pluginClassName, true, Thread.currentThread().getContextClassLoader());
            return (JdbiPlugin) pluginClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            throw new IllegalStateException("Failed to load Jdbi plugin: " + pluginClassName, ex);
        }
    }
}
