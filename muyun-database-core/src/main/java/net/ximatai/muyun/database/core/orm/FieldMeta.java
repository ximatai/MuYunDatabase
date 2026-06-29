package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.Optional;

/**
 * Runtime-readable field metadata used by Criteria compilation and value codecs.
 */
public interface FieldMeta {

    String getFieldName();

    String getColumnName();

    ColumnType getColumnType();

    ColumnType getElementColumnType();

    boolean isId();

    Class<?> getFieldType();

    Optional<Class<?>> getCollectionElementType();
}
