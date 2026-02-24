package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.annotation.*;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityMetaResolver {

    private final Map<Class<?>, EntityMeta> cache = new ConcurrentHashMap<>();

    public EntityMeta resolve(Class<?> entityClass) {
        if (entityClass == null) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, "entityClass must not be null");
        }
        return cache.computeIfAbsent(entityClass, this::buildMeta);
    }

    private EntityMeta buildMeta(Class<?> entityClass) {
        TableWrapper wrapper;
        try {
            wrapper = AnnotationProcessor.fromEntityClass(entityClass);
        } catch (RuntimeException e) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Failed to parse entity annotations: " + entityClass.getName(), e);
        }

        List<EntityFieldMeta> fields = new ArrayList<>();
        EntityFieldMeta idField = null;

        for (Field field : AnnotationProcessor.getAllFields(entityClass)) {
            if (!field.isAnnotationPresent(Column.class) && !field.isAnnotationPresent(Id.class)) {
                continue;
            }

            Column column = field.getAnnotation(Column.class);
            Id id = field.getAnnotation(Id.class);
            boolean isId = id != null;

            String columnName = resolveColumnName(field, column, id);
            ColumnType columnType = resolveColumnType(field, column);

            EntityFieldMeta fieldMeta = new EntityFieldMeta(field, columnName, columnType, isId);
            fields.add(fieldMeta);

            if (isId) {
                idField = fieldMeta;
            }
        }

        if (idField == null) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "No @Id field found in class: " + entityClass.getName());
        }

        return new EntityMeta(entityClass, wrapper.getName(), wrapper.getSchema(), wrapper, fields, idField);
    }

    private String resolveColumnName(Field field, Column column, Id id) {
        if (id != null && !id.name().isEmpty()) {
            return id.name();
        }
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        return field.getName();
    }

    private ColumnType resolveColumnType(Field field, Column column) {
        if (column != null && column.type() != ColumnType.UNKNOWN) {
            return column.type();
        }
        return TypeMapper.inferSqlType(field.getType());
    }
}
