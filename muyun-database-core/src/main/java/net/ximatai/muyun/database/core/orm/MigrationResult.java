package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MigrationResult {
    private final boolean changed;
    private final boolean dryRun;
    private final boolean hasNonAdditiveChanges;
    private final List<String> statements;

    public MigrationResult(boolean changed, boolean dryRun, boolean hasNonAdditiveChanges, List<String> statements) {
        this.changed = changed;
        this.dryRun = dryRun;
        this.hasNonAdditiveChanges = hasNonAdditiveChanges;
        this.statements = new ArrayList<>(statements);
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
}
