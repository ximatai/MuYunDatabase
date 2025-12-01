package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 注解转化工具
 */
public class AnnotationProcessor {

    /**
     * 获取所有转化后的数据库字段
     *
     * @param type
     * @return
     */
    public static List<Field> getAllFields(Class<?> type) {

        List<Field> fields = new ArrayList<>(Arrays.asList(type.getDeclaredFields()));

        Class<?> superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            List<Field> superFields = getAllFields(superClass);
            for (Field field : superFields) {
                if (fields.stream().noneMatch(f -> f.getName().equals(field.getName()))) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * 从实体类中获取表信息
     *
     * @param entityClass
     * @return
     */
    public static TableWrapper fromEntityClass(Class<?> entityClass) {

        Table tableAnnotation = findTableAnnotation(entityClass);
        if (tableAnnotation == null) {
            throw new IllegalArgumentException("Class or its superclass must be annotated with @Table");
        }

        TableWrapper tableWrapper = TableWrapper.withName(tableAnnotation.name());
        if (!tableAnnotation.comment().isEmpty()) {
            tableWrapper.setComment(tableAnnotation.comment());
        }
        if (!tableAnnotation.schema().isEmpty()) {
            tableWrapper.setSchema(tableAnnotation.schema());
        }

        // 处理所有字段
        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)) {
                Column columnAnnotation = field.getAnnotation(Column.class);
                Id idAnnotation = field.getAnnotation(Id.class);

                if (idAnnotation != null) {
                    if (!idAnnotation.value().equals(PredefinedColumn.Id.CUSTOM)) {
                        tableWrapper.addColumn(idAnnotation.value().toColumn());
                        continue;
                    }
                }

                // 确定列名
                String columnName;
                if (idAnnotation != null && !idAnnotation.name().isEmpty()) {
                    columnName = idAnnotation.name();
                } else if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                    columnName = columnAnnotation.name();
                } else {
                    columnName = field.getName();
                }

                // 创建列
                net.ximatai.muyun.database.core.builder.Column column = net.ximatai.muyun.database.core.builder.Column.of(columnName);

                // 处理Column注解属性
                if (columnAnnotation != null) {
                    if (columnAnnotation.type().equals(ColumnType.UNKNOWN)) {
                        column.setType(TypeMapper.inferSqlType(field.getType()));
                    } else {
                        column.setType(columnAnnotation.type());
                    }
                    column.setNullable(columnAnnotation.nullable());

                    if (columnAnnotation.length() > 0) {
                        column.setLength(columnAnnotation.length());
                    }
                    if (columnAnnotation.precision() > 0) {
                        column.setPrecision(columnAnnotation.precision());
                    }
                    if (columnAnnotation.scale() > 0) {
                        column.setScale(columnAnnotation.scale());
                    }
                    if (!columnAnnotation.comment().isEmpty()) {
                        column.setComment(columnAnnotation.comment());
                    }
                }

                if (field.isAnnotationPresent(Indexed.class)) {
                    Indexed indexed = field.getAnnotation(Indexed.class);
                    if (indexed.unique()) {
                        column.setUnique();
                    } else {
                        column.setIndexed();
                    }

                }

                if (field.isAnnotationPresent(Sequence.class)) {
                    column.setSequence();
                }

                Default defaultVal = columnAnnotation.defaultVal();

                if (defaultVal != null) {

                    if (!defaultVal.function().isEmpty()) {
                        column.setDefaultValueAny(defaultVal.function());
                    } else if (!defaultVal.express().isEmpty()) {
                        column.setDefaultValueAny(defaultVal.express());
                    } else if (!defaultVal.varchar().isEmpty()) {
                        column.setDefaultValueAny("'" + defaultVal.varchar() + "'");
                    } else if (defaultVal.number() > Long.MIN_VALUE) {
                        column.setDefaultValueAny(String.valueOf(defaultVal.number()));
                    } else if (defaultVal.decimal() > Double.MIN_VALUE) {
                        column.setDefaultValueAny(String.valueOf(defaultVal.decimal()));
                    } else if (defaultVal.bool().equals(TrueOrFalse.TRUE)) {
                        column.setDefaultValueAny("TRUE");
                    } else if (defaultVal.bool().equals(TrueOrFalse.FALSE)) {
                        column.setDefaultValueAny("FALSE");
                    } else if (defaultVal.nullVal()) {
                        column.setDefaultValueAny("NULL");
                    }

                }

                if (idAnnotation != null) {
                    tableWrapper.setPrimaryKey(column);
                } else {
                    tableWrapper.addColumn(column);
                }

            }
        }

        findAllCompositeIndex(entityClass).forEach(compositeIndex -> addCompositeIndexToTable(tableWrapper, compositeIndex));

        return tableWrapper;
    }

    /**
     * 获取所有的联合索引
     *
     * @param
     * @return
     */
    private static List<CompositeIndex> findAllCompositeIndex(Class<?> clazz) {
        List<CompositeIndex> indexes = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            if (clazz.isAnnotationPresent(CompositeIndex.class)) {
                indexes.add(clazz.getAnnotation(CompositeIndex.class));
            }
            if (clazz.isAnnotationPresent(CompositeIndexes.class)) {
                indexes.addAll(Arrays.asList(clazz.getAnnotation(CompositeIndexes.class).value()));
            }

            clazz = clazz.getSuperclass();
        }

        return indexes;
    }

    private static Table findTableAnnotation(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            if (clazz.isAnnotationPresent(Table.class)) {
                return clazz.getAnnotation(Table.class);
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static void addCompositeIndexToTable(TableWrapper tableWrapper, CompositeIndex compositeIndex) {
        Index index = new Index(Arrays.asList(compositeIndex.columns()), compositeIndex.unique());
        index.setName(compositeIndex.name());
        tableWrapper.addIndex(index);
    }
}
