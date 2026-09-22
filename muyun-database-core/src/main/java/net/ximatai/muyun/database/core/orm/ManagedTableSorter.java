package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ManagedTableSorter {
    private ManagedTableSorter() {
    }

    public static List<ManagedTable> sort(Collection<ManagedTable> tables) {
        Objects.requireNonNull(tables, "managed tables must not be null");
        LinkedHashMap<String, ManagedTable> byId = new LinkedHashMap<>();
        Map<String, String> idByQualifiedTable = new HashMap<>();
        for (ManagedTable table : tables) {
            Objects.requireNonNull(table, "managed table must not be null");
            if (byId.putIfAbsent(table.id(), table) != null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "duplicate managed table id: " + table.id());
            }
            String schema = table.table().getSchema() == null ? "" : table.table().getSchema();
            String qualifiedTable = schema + "." + table.table().getName();
            String existingId = idByQualifiedTable.putIfAbsent(qualifiedTable, table.id());
            if (existingId != null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        "managed table " + qualifiedTable + " is declared by both " + existingId + " and " + table.id());
            }
        }

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (ManagedTable table : tables) {
            LinkedHashSet<String> resolved = new LinkedHashSet<>(table.dependsOn());
            String localSchema = table.table().getSchema() == null ? "" : table.table().getSchema();
            for (ForeignKeyConstraint foreignKey : table.table().getForeignKeys()) {
                String referencedSchema = foreignKey.referencedSchema() == null ? localSchema : foreignKey.referencedSchema();
                String dependency = idByQualifiedTable.get(referencedSchema + "." + foreignKey.referencedTable());
                if (dependency != null && !dependency.equals(table.id())) {
                    resolved.add(dependency);
                }
            }
            for (String dependency : resolved) {
                if (!byId.containsKey(dependency)) {
                    throw new OrmException(OrmException.Code.INVALID_MAPPING,
                            "managed table " + table.id() + " depends on unknown table id: " + dependency);
                }
            }
            dependencies.put(table.id(), resolved);
        }

        List<ManagedTable> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : byId.keySet()) {
            visit(id, byId, dependencies, visiting, visited, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(String id,
                              Map<String, ManagedTable> byId,
                              Map<String, Set<String>> dependencies,
                              Set<String> visiting,
                              Set<String> visited,
                              List<ManagedTable> ordered) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "cyclic managed table dependency involving: " + id);
        }
        for (String dependency : dependencies.getOrDefault(id, Set.of())) {
            visit(dependency, byId, dependencies, visiting, visited, ordered);
        }
        visiting.remove(id);
        visited.add(id);
        ordered.add(byId.get(id));
    }
}
