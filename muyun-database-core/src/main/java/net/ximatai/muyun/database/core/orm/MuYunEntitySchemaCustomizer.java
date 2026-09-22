package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Adds schema-only details to the table model resolved from an ORM entity.
 *
 * <p>The customization becomes part of the entity's cached metadata, so ORM
 * mapping and schema reconciliation share one table model. A customizer may
 * add table-level constraints and indexes, but must not rename or redefine
 * ORM-mapped columns and must not execute DDL.</p>
 */
public final class MuYunEntitySchemaCustomizer<T> {

    private final Class<T> entityClass;
    private final Consumer<TableWrapper> customization;

    private MuYunEntitySchemaCustomizer(Class<T> entityClass, Consumer<TableWrapper> customization) {
        this.entityClass = Objects.requireNonNull(entityClass, "entity class must not be null");
        this.customization = Objects.requireNonNull(customization, "schema customization must not be null");
    }

    public static <T> MuYunEntitySchemaCustomizer<T> forEntity(
            Class<T> entityClass,
            Consumer<TableWrapper> customization
    ) {
        return new MuYunEntitySchemaCustomizer<>(entityClass, customization);
    }

    public Class<T> entityClass() {
        return entityClass;
    }

    void customize(TableWrapper table) {
        customization.accept(table);
    }
}
