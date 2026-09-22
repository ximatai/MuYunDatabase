package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record ManagedTable(String id, TableWrapper table, Set<String> dependsOn) {
    public ManagedTable {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("managed table id must not be blank");
        }
        table = Objects.requireNonNull(table, "managed table must not be null");
        dependsOn = dependsOn == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(dependsOn));
    }

    public static ManagedTable of(String id, TableWrapper table) {
        return new ManagedTable(id, table, Set.of());
    }
}
