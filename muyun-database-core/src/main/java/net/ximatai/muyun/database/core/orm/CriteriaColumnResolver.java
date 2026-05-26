package net.ximatai.muyun.database.core.orm;

@FunctionalInterface
public interface CriteriaColumnResolver {

    /**
     * Resolves a criteria field name to a physical database column name.
     *
     * @param field criteria field name
     * @return column name, or null when the field is unknown
     */
    String resolveColumnName(String field);
}
