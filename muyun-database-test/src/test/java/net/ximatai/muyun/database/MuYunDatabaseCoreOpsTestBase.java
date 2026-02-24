package net.ximatai.muyun.database;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

public abstract class MuYunDatabaseCoreOpsTestBase extends MuYunDatabaseBaseTest {

    @Test
    @Override
    protected void testGetDBInfo() {
        super.testGetDBInfo();
    }

    @Test
    @Override
    protected void testSimpleInsert() {
        super.testSimpleInsert();
    }

    @Test
    @Override
    protected void testUpsert() {
        super.testUpsert();
    }

    @Test
    @Override
    protected void testBatchInsert() {
        super.testBatchInsert();
    }

    @Test
    @Override
    protected void testUpdate() {
        super.testUpdate();
    }

    @Test
    @Override
    protected void testPatchUpdate() {
        super.testPatchUpdate();
    }

    @Test
    @Override
    protected void testDelete() {
        super.testDelete();
    }

    @Test
    @Override
    protected void testQuery() {
        super.testQuery();
    }

    @Test
    @Override
    protected void testJdbiConnectionClosedWhenException() throws SQLException {
        super.testJdbiConnectionClosedWhenException();
    }

    @Test
    @Override
    protected void testJdbiConnectionClosedWhenException2() throws SQLException {
        super.testJdbiConnectionClosedWhenException2();
    }

    @Test
    @Override
    protected void testJdbiConnectionClosedWhenException3() throws SQLException {
        super.testJdbiConnectionClosedWhenException3();
    }

    @Test
    @Override
    protected void testAtomicUpsertConcurrent() throws InterruptedException {
        super.testAtomicUpsertConcurrent();
    }

    @Test
    @Override
    protected void testAtomicUpsertConcurrentHighContention() throws InterruptedException {
        super.testAtomicUpsertConcurrentHighContention();
    }
}
