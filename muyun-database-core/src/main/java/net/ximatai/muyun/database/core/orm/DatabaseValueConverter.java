package net.ximatai.muyun.database.core.orm;

/**
 * Converts values between entity/query values and database-bound values.
 * <p>
 * The default converter stores enums by {@link Enum#name()}. Applications that
 * persist enums by a separate code or value should provide a custom converter
 * and implement both directions consistently.
 */
public interface DatabaseValueConverter {

    DatabaseValueConverter DEFAULT = new DefaultDatabaseValueConverter();

    /**
     * Converts an application value before it is bound to SQL or written to a row map.
     */
    Object toDatabaseValue(Object value);

    /**
     * Converts a database value before it is assigned to an entity field.
     */
    Object fromDatabaseValue(Object value, Class<?> targetType);
}
