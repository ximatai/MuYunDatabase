package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.orm.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public abstract class MuYunDatabaseOrmTestBase extends MuYunDatabaseDdlTestBase {

    @Test
    void testSimpleOrmCrudWithAnnotatedEntity() throws Exception {
        Class<?> entityClass = getEntityClass();
        orm.ensureTable(entityClass);

        Object entity = entityClass.getDeclaredConstructor().newInstance();
        setField(entity, "code", 10 + (int) (System.currentTimeMillis() % 10000));
        setField(entity, "name", "orm_user");
        setField(entity, "age", 15);
        setField(entity, "flag", true);

        Object id = orm.insert(entity);
        assertNotNull(id);
        assertEquals(id, getField(entity, "id"));

        Object loaded = orm.findById((Class) entityClass, id);
        assertNotNull(loaded);
        assertEquals("orm_user", getField(loaded, "name"));
        assertEquals(15, getField(loaded, "age"));

        setField(loaded, "name", "orm_user_v2");
        int updated = orm.update(loaded);
        assertEquals(1, updated);

        Object reloaded = orm.findById((Class) entityClass, id);
        assertNotNull(reloaded);
        assertEquals("orm_user_v2", getField(reloaded, "name"));

        int deleted = orm.deleteById((Class) entityClass, id);
        assertEquals(1, deleted);
    }

    @Test
    void testSimpleOrmUpdateIgnoreNulls() {
        orm.ensureTable(OrmPatchEntity.class);

        OrmPatchEntity created = new OrmPatchEntity();
        created.id = UUID.randomUUID().toString();
        created.name = "patch_source";
        created.age = 11;
        orm.insert(created);

        OrmPatchEntity patch = new OrmPatchEntity();
        patch.id = created.id;
        patch.name = null;
        patch.age = 20;

        int updated = orm.update(patch, NullUpdateStrategy.IGNORE_NULLS);
        assertEquals(1, updated);

        OrmPatchEntity loaded = orm.findById(OrmPatchEntity.class, created.id);
        assertNotNull(loaded);
        assertEquals("patch_source", loaded.name);
        assertEquals(20, loaded.age);
    }

    @Test
    void testSimpleOrmMigrationDryRun() {
        MigrationResult result = orm.ensureTable(OrmDryRunEntity.class, MigrationOptions.dryRun());
        assertTrue(result.isDryRun());
        assertTrue(result.isChanged());
        assertFalse(result.getStatements().isEmpty());

        loader.resetInfo();
        assertFalse(loader.getDBInfo().getDefaultSchema().containsTable("orm_dryrun_entity"));
    }

    @Test
    void testSimpleOrmMigrationStrictRejectsNonAdditive() {
        orm.ensureTable(OrmStrictEntityV1.class);
        loader.resetInfo();

        OrmException exception = assertThrows(
                OrmException.class,
                () -> orm.ensureTable(OrmStrictEntityV2.class, MigrationOptions.dryRunStrict())
        );

        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, exception.getCode());
    }

    @Test
    void testSimpleOrmQueryWithSort() {
        orm.ensureTable(OrmPatchEntity.class);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = "sort_user";
        row1.age = 10;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = "sort_user";
        row2.age = 20;
        orm.insert(row2);

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                Criteria.of().eq("v_name", "sort_user"),
                new PageRequest(0, 10),
                Sort.desc("i_age")
        );

        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().age >= rows.get(1).age);
    }

    @Test
    void testSimpleOrmPageQueryResult() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "page_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 10;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 20;
        orm.insert(row2);

        OrmPatchEntity row3 = new OrmPatchEntity();
        row3.id = UUID.randomUUID().toString();
        row3.name = marker;
        row3.age = 30;
        orm.insert(row3);

        PageResult<OrmPatchEntity> page = orm.pageQuery(
                OrmPatchEntity.class,
                Criteria.of().eq("v_name", marker),
                PageRequest.of(2, 1),
                Sort.asc("i_age")
        );

        assertEquals(3, page.getTotal());
        assertEquals(3, page.getPages());
        assertEquals(2, page.getPageNum());
        assertEquals(1, page.getPageSize());
        assertEquals(1, page.getRecords().size());
        assertEquals(20, page.getRecords().getFirst().age);
    }

    @Test
    void testSimpleOrmQueryWithOrGroupNested() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "or_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 10;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 30;
        orm.insert(row2);

        OrmPatchEntity row3 = new OrmPatchEntity();
        row3.id = UUID.randomUUID().toString();
        row3.name = marker + "_x";
        row3.age = 40;
        orm.insert(row3);

        Criteria criteria = Criteria.of()
                .orGroup(g -> g.eq("v_name", marker).andGroup(gg -> gg.gt("i_age", 5).lt("i_age", 15)))
                .orGroup(g -> g.eq("v_name", marker).gt("i_age", 25));

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(2, rows.size());
        assertEquals(10, rows.get(0).age);
        assertEquals(30, rows.get(1).age);
    }

    @Test
    void testSimpleOrmQueryWithInSubQuery() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "sub_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 12;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 30;
        orm.insert(row2);

        Criteria criteria = Criteria.of().inSubQuery(
                "id",
                SqlSubQuery.of(
                        "select id from orm_patch_entity where v_name = :name and i_age >= :age",
                        Map.of("name", marker, "age", 20)
                )
        );

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(1, rows.size());
        assertEquals(30, rows.getFirst().age);
    }

    @Test
    void testSimpleOrmQueryWithNotInSubQuery() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "nsub_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 12;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 30;
        orm.insert(row2);

        Criteria criteria = Criteria.of()
                .eq("v_name", marker)
                .notInSubQuery(
                        "id",
                        SqlSubQuery.of(
                                "select id from orm_patch_entity where v_name = :name and i_age >= :age",
                                Map.of("name", marker, "age", 20)
                        )
                );

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(1, rows.size());
        assertEquals(12, rows.getFirst().age);
    }

    @Test
    void testSimpleOrmQueryWithNotInValues() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "notin_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 12;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 30;
        orm.insert(row2);

        Criteria criteria = Criteria.of()
                .eq("v_name", marker)
                .notIn("i_age", List.of(30, 40));

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(1, rows.size());
        assertEquals(12, rows.getFirst().age);
    }

    @Test
    void testSimpleOrmQueryWithExistsSubQuery() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "exists_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 11;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 22;
        orm.insert(row2);

        Criteria criteria = Criteria.of()
                .eq("v_name", marker)
                .exists(SqlSubQuery.of(
                        "select 1 from orm_patch_entity where v_name = :name and i_age >= :age",
                        Map.of("name", marker, "age", 20)
                ));

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 20),
                Sort.asc("i_age")
        );

        assertEquals(2, rows.size());
        assertEquals(11, rows.get(0).age);
        assertEquals(22, rows.get(1).age);
    }

    @Test
    void testSimpleOrmQueryWithWhenCondition() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "when_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 12;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 22;
        orm.insert(row2);

        Criteria criteria = Criteria.of()
                .eq("v_name", marker)
                .when(false, c -> c.gt("i_age", 20));

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(2, rows.size());
    }

    @Test
    void testSimpleOrmQueryWithRawCondition() {
        orm.ensureTable(OrmPatchEntity.class);
        String marker = "raw_" + UUID.randomUUID().toString().substring(0, 8);

        OrmPatchEntity row1 = new OrmPatchEntity();
        row1.id = UUID.randomUUID().toString();
        row1.name = marker;
        row1.age = 13;
        orm.insert(row1);

        OrmPatchEntity row2 = new OrmPatchEntity();
        row2.id = UUID.randomUUID().toString();
        row2.name = marker;
        row2.age = 33;
        orm.insert(row2);

        Criteria criteria = Criteria.of()
                .eq("v_name", marker)
                .raw(SqlRawCondition.of("i_age >= :age", Map.of("age", 20)));

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                criteria,
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );

        assertEquals(1, rows.size());
        assertEquals(33, rows.getFirst().age);
    }

    @Test
    void testCrudRepositoryFlow() {
        orm.ensureTable(OrmPatchEntity.class);
        CrudRepository<OrmPatchEntity, String> repo = SimpleOrm.repository(OrmPatchEntity.class, orm);

        OrmPatchEntity entity = new OrmPatchEntity();
        entity.id = UUID.randomUUID().toString();
        entity.name = "repo_user";
        entity.age = 18;

        String id = repo.insert(entity);
        assertEquals(entity.id, id);

        OrmPatchEntity loaded = repo.findById(id);
        assertNotNull(loaded);
        assertEquals("repo_user", loaded.name);

        loaded.age = 20;
        assertEquals(1, repo.update(loaded));

        PageResult<OrmPatchEntity> page = repo.pageQuery(
                Criteria.of().eq("v_name", "repo_user"),
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(20, page.getRecords().getFirst().age);

        assertEquals(1, repo.deleteById(id));
    }

    private Object getField(Object target, String name) throws Exception {
        var field = findField(target.getClass(), name);
        assertNotNull(field);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        var field = findField(target.getClass(), name);
        assertNotNull(field);
        field.setAccessible(true);
        field.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
