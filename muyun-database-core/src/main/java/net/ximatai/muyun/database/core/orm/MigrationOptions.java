package net.ximatai.muyun.database.core.orm;

public class MigrationOptions {
    private final boolean dryRun;
    private final boolean strict;

    public MigrationOptions(boolean dryRun, boolean strict) {
        this.dryRun = dryRun;
        this.strict = strict;
    }

    public static MigrationOptions execute() {
        return new MigrationOptions(false, false);
    }

    public static MigrationOptions dryRun() {
        return new MigrationOptions(true, false);
    }

    public static MigrationOptions strict() {
        return new MigrationOptions(false, true);
    }

    public static MigrationOptions dryRunStrict() {
        return new MigrationOptions(true, true);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isStrict() {
        return strict;
    }
}
