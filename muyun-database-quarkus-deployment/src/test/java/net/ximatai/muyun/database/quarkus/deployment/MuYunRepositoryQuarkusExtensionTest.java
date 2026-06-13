package net.ximatai.muyun.database.quarkus.deployment;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import net.ximatai.muyun.database.quarkus.MuYunRepositoryFactory;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuYunRepositoryQuarkusExtensionTest {

    @Test
    void processorRegistersRepositoryInterfacesAsSyntheticBeans() throws IOException {
        Index index = indexOf(TestRepository.class);
        List<DotName> repositories = new MuYunDatabaseQuarkusProcessor().repositoryInterfaces(index);

        assertEquals(1, repositories.size());
        assertEquals(TestRepository.class.getName(), repositories.getFirst().toString());
    }

    @Test
    void processorRejectsRepositoryClasses() throws IOException {
        Index index = indexOf(InvalidRepositoryClass.class);

        assertThrows(IllegalStateException.class, () -> new MuYunDatabaseQuarkusProcessor()
                .repositoryInterfaces(index));
    }

    @Test
    void repositoryFactoryCreatesProxyAndInvokesDefaultMethods() {
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(fakeOperations(), null, fakeEntityManager());

        TestRepository repository = factory.create(TestRepository.class);

        assertNotNull(repository);
        assertTrue(Proxy.isProxyClass(repository.getClass()));
        assertEquals("pong", repository.ping());
    }

    private static Index indexOf(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = clazz.getName().replace('.', '/') + ".class";
            try (InputStream input = clazz.getClassLoader().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Class resource not found: " + resourceName);
                }
                indexer.index(input);
            }
        }
        return indexer.complete();
    }

    private static IDatabaseOperations<?> fakeOperations() {
        return new IDatabaseOperations<>() {
            @Override
            public IMetaDataLoader getMetaDataLoader() {
                return new IMetaDataLoader() {
                    @Override
                    public DBInfo getDBInfo() {
                        return new DBInfo("PostgreSQL").setName("test");
                    }

                    @Override
                    public void resetInfo() {
                    }

                    @Override
                    public List<net.ximatai.muyun.database.core.metadata.DBIndex> getIndexList(String schema, String table) {
                        return List.of();
                    }

                    @Override
                    public Map<String, net.ximatai.muyun.database.core.metadata.DBColumn> getColumnMap(String schema, String table) {
                        return Map.of();
                    }
                };
            }

            @Override
            public String getPKName() {
                return "id";
            }

            @Override
            public Object insert(String sql, Map<String, Object> params) {
                return null;
            }

            @Override
            public Object insertWithPK(String sql, Map<String, Object> params, Object pk) {
                return pk;
            }

            @Override
            public List<Object> batchInsert(String sql, List<Map<String, Object>> paramsList) {
                return List.of();
            }

            @Override
            public Map<String, Object> row(String sql, List<Object> params) {
                return Map.of();
            }

            @Override
            public Map<String, Object> row(String sql, Map<String, Object> params) {
                return Map.of();
            }

            @Override
            public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
                return List.of();
            }

            @Override
            public List<Map<String, Object>> query(String sql, List<Object> params) {
                return List.of();
            }

            @Override
            public int update(String sql, Map<String, Object> params) {
                return 0;
            }

            @Override
            public int update(String sql, List<Object> params) {
                return 0;
            }

            @Override
            public int execute(String sql) {
                return 0;
            }

            @Override
            public int execute(String sql, Object... params) {
                return 0;
            }

            @Override
            public int execute(String sql, List<Object> params) {
                return 0;
            }

            @Override
            public Array createArray(List<Object> list, String type) {
                return null;
            }
        };
    }

    private static SimpleEntityManager fakeEntityManager() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "ensureTable" -> method.getReturnType() == MigrationResult.class
                    ? MigrationResult.empty((MigrationOptions) args[1])
                    : false;
            case "exists" -> false;
            case "count" -> 0L;
            case "deleteById", "update", "upsert" -> 0;
            case "findById", "insert" -> null;
            case "findAll", "query" -> List.of();
            default -> throw new UnsupportedOperationException(method.toString());
        };
        return (SimpleEntityManager) Proxy.newProxyInstance(
                SimpleEntityManager.class.getClassLoader(),
                new Class<?>[]{SimpleEntityManager.class},
                handler
        );
    }

    @MuYunRepository
    interface TestRepository extends EntityDao<TestEntity, String> {

        default String ping() {
            return "pong";
        }
    }

    @MuYunRepository
    static class InvalidRepositoryClass {
    }

    @Table(name = "quarkus_test_entity")
    public static class TestEntity {
        @Id
        public String id;
    }
}
