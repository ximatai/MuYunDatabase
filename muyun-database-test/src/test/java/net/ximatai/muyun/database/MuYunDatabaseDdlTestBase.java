package net.ximatai.muyun.database;

import org.junit.jupiter.api.Test;

public abstract class MuYunDatabaseDdlTestBase extends MuYunDatabaseCoreOpsTestBase {

    @Test
    @Override
    protected void testTableBuilder() {
        super.testTableBuilder();
    }

    @Test
    @Override
    protected void testTableBuilderChangeLength() {
        super.testTableBuilderChangeLength();
    }

    @Test
    @Override
    protected void testTableBuilderWithoutDefaultSchema() {
        super.testTableBuilderWithoutDefaultSchema();
    }

    @Test
    @Override
    protected void testModifyColumnTypeToText() {
        super.testModifyColumnTypeToText();
    }

    @Test
    @Override
    protected void testInherit() {
        super.testInherit();
    }

    @Test
    @Override
    protected void testInheritLater() {
        super.testInheritLater();
    }

    @Test
    @Override
    protected void testInheritMultiParentsForPostgres() {
        super.testInheritMultiParentsForPostgres();
    }

    @Test
    @Override
    protected void testTableBuilderWithEntity() {
        super.testTableBuilderWithEntity();
    }

    @Test
    @Override
    protected void testTableBuilderWithEntityNoIdDefaultValue() {
        super.testTableBuilderWithEntityNoIdDefaultValue();
    }

    @Test
    @Override
    protected void testIndexChange() {
        super.testIndexChange();
    }

    @Test
    @Override
    protected void testSpecialTableNameSupport() {
        super.testSpecialTableNameSupport();
    }
}
