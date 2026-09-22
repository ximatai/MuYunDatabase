package net.ximatai.muyun.database.core.orm;

import java.util.List;
import java.util.Objects;

public class MigrationResult {
    private final boolean changed;
    private final boolean dryRun;
    private final boolean hasNonAdditiveChanges;
    private final List<String> statements;
    private final List<MigrationChange> changes;

    public MigrationResult(boolean changed, boolean dryRun, boolean hasNonAdditiveChanges, List<String> statements) {
        this(changed, dryRun, statements.stream()
                .map(sql -> new MigrationChange(MigrationChange.Type.RAW_SQL, null, sql, hasNonAdditiveChanges))
                .toList());
    }

    public MigrationResult(boolean changed,
                           boolean dryRun,
                           boolean hasNonAdditiveChanges,
                           List<String> statements,
                           List<MigrationChange> changes) {
        this(changed, dryRun, validatedChanges(hasNonAdditiveChanges, statements, changes));
    }

    public MigrationResult(boolean changed, boolean dryRun, List<MigrationChange> changes) {
        this.changed = changed;
        this.dryRun = dryRun;
        this.changes = List.copyOf(changes);
        this.statements = this.changes.stream().map(MigrationChange::getSql).toList();
        this.hasNonAdditiveChanges = this.changes.stream().anyMatch(MigrationChange::isNonAdditive);
    }

    public static MigrationResult empty(MigrationOptions options) {
        return new MigrationResult(false, options.isDryRun(), List.of());
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean hasNonAdditiveChanges() {
        return hasNonAdditiveChanges;
    }

    public List<String> getStatements() {
        return statements;
    }

    public List<MigrationChange> getChanges() {
        return changes;
    }

    private static List<MigrationChange> validatedChanges(boolean hasNonAdditiveChanges,
                                                          List<String> statements,
                                                          List<MigrationChange> changes) {
        Objects.requireNonNull(statements, "statements must not be null");
        Objects.requireNonNull(changes, "changes must not be null");
        List<String> changeStatements = changes.stream().map(MigrationChange::getSql).toList();
        if (!statements.equals(changeStatements)) {
            throw new IllegalArgumentException("migration statements must match change SQL order");
        }
        boolean actual = changes.stream().anyMatch(MigrationChange::isNonAdditive);
        if (hasNonAdditiveChanges != actual) {
            throw new IllegalArgumentException("migration non-additive flag must match change details");
        }
        return changes;
    }
}
