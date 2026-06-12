package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MigrationResult {
    private final boolean changed;
    private final boolean dryRun;
    private final boolean hasNonAdditiveChanges;
    private final List<String> statements;
    private final List<MigrationChange> changes;

    public MigrationResult(boolean changed, boolean dryRun, boolean hasNonAdditiveChanges, List<String> statements) {
        this.changed = changed;
        this.dryRun = dryRun;
        this.hasNonAdditiveChanges = hasNonAdditiveChanges;
        this.statements = new ArrayList<>(statements);
        this.changes = statements.stream()
                .map(sql -> new MigrationChange(MigrationChange.Type.RAW_SQL, null, sql, hasNonAdditiveChanges))
                .toList();
    }

    public MigrationResult(boolean changed,
                           boolean dryRun,
                           boolean hasNonAdditiveChanges,
                           List<String> statements,
                           List<MigrationChange> changes) {
        this.changed = changed;
        this.dryRun = dryRun;
        this.hasNonAdditiveChanges = hasNonAdditiveChanges;
        this.statements = new ArrayList<>(statements);
        this.changes = new ArrayList<>(changes);
        requireStatementsMatchChanges(this.statements, this.changes);
    }

    public static MigrationResult empty(MigrationOptions options) {
        return new MigrationResult(false, options.isDryRun(), false, List.of());
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
        return Collections.unmodifiableList(statements);
    }

    public List<MigrationChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    private static void requireStatementsMatchChanges(List<String> statements, List<MigrationChange> changes) {
        Objects.requireNonNull(statements, "statements must not be null");
        Objects.requireNonNull(changes, "changes must not be null");
        List<String> changeStatements = changes.stream().map(MigrationChange::getSql).toList();
        if (!statements.equals(changeStatements)) {
            throw new IllegalArgumentException("migration statements must match change SQL order");
        }
    }
}
