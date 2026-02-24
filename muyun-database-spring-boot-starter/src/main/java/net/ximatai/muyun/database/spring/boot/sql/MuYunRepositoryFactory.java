package net.ximatai.muyun.database.spring.boot.sql;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.EntityMetaResolver;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.PageResult;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.springframework.core.env.Environment;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MuYunRepositoryFactory {

    private final IDatabaseOperations<?> operations;
    @SuppressWarnings("unused")
    private final Environment environment;
    private final Jdbi jdbi;

    public MuYunRepositoryFactory(IDatabaseOperations<?> operations, Environment environment) {
        this(operations, environment, null);
    }

    public MuYunRepositoryFactory(IDatabaseOperations<?> operations, Environment environment, Jdbi jdbi) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.jdbi = jdbi;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> daoType) {
        Objects.requireNonNull(daoType, "daoType");
        if (!daoType.isInterface()) {
            throw new IllegalArgumentException("MuYun repository must be an interface: " + daoType.getName());
        }
        InvocationHandler handler = new DaoInvocationHandler(daoType);
        return (T) Proxy.newProxyInstance(
                daoType.getClassLoader(),
                new Class<?>[]{daoType},
                handler
        );
    }

    private final class DaoInvocationHandler implements InvocationHandler {

        private final Class<?> daoType;
        private final Map<Method, EntityDaoMethodType> entityDaoMethodTypes = new ConcurrentHashMap<>();
        private final EntityDaoDelegate entityDaoDelegate;

        private DaoInvocationHandler(Class<?> daoType) {
            this.daoType = daoType;
            this.entityDaoDelegate = resolveEntityDaoDelegate(daoType);

            for (Method method : daoType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                validateMethod(method);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] safeArgs = args == null ? new Object[0] : args;

            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, safeArgs);
            }
            if (method.isDefault()) {
                return invokeDefault(proxy, method, safeArgs);
            }

            EntityDaoMethodType type = entityDaoMethodTypes.computeIfAbsent(
                    method,
                    m -> entityDaoDelegate == null ? EntityDaoMethodType.NONE : entityDaoDelegate.resolve(m)
            );
            if (type != EntityDaoMethodType.NONE) {
                return entityDaoDelegate.invoke(type, safeArgs);
            }
            return invokeViaJdbi(method, safeArgs);
        }

        private void validateMethod(Method method) {
            if (!daoType.isAnnotationPresent(MuYunRepository.class)) {
                throw new IllegalStateException("DAO interface must be annotated with @MuYunRepository: " + daoType.getName());
            }

            EntityDaoMethodType type = entityDaoDelegate == null ? EntityDaoMethodType.NONE : entityDaoDelegate.resolve(method);
            entityDaoMethodTypes.put(method, type);
            if (entityDaoDelegate != null) {
                entityDaoDelegate.validate(method, type);
            }

            if (type != EntityDaoMethodType.NONE) {
                if (hasJdbiSqlAnnotation(method)) {
                    throw new IllegalStateException("CRUD method is reserved by EntityDao and must not use SQL annotations: " + method);
                }
                return;
            }

            if (!hasJdbiSqlAnnotation(method)) {
                if (entityDaoDelegate != null) {
                    throw new IllegalStateException("Non-CRUD method must use Jdbi SQL annotations: " + method);
                }
                throw new IllegalStateException("Method must use Jdbi SQL annotations: " + method);
            }

            if (jdbi == null) {
                throw new IllegalStateException("Jdbi SQL Object annotations require Jdbi bean: " + method);
            }
        }

        private Object invokeViaJdbi(Method method, Object[] args) throws Throwable {
            try {
                return jdbi.withExtension(daoType, extension -> {
                    try {
                        return method.invoke(extension, args);
                    } catch (InvocationTargetException ex) {
                        Throwable cause = ex.getCause();
                        if (cause instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new RuntimeException(cause);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (RuntimeException ex) {
                Throwable cause = ex.getCause();
                if (cause != null && !(cause instanceof RuntimeException)) {
                    throw cause;
                }
                throw ex;
            }
        }

        private EntityDaoDelegate resolveEntityDaoDelegate(Class<?> type) {
            EntityDaoTypes daoTypes = resolveEntityDaoTypes(type);
            if (daoTypes == null) {
                return null;
            }
            if (!type.isAnnotationPresent(MuYunRepository.class)) {
                throw new IllegalStateException("EntityDao interface must use @MuYunRepository: " + type.getName());
            }
            return new EntityDaoDelegate(daoTypes.entityType());
        }
    }

    private EntityDaoTypes resolveEntityDaoTypes(Class<?> daoType) {
        for (Type genericInterface : daoType.getGenericInterfaces()) {
            EntityDaoTypes hit = resolveEntityDaoTypes(genericInterface);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private EntityDaoTypes resolveEntityDaoTypes(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw == EntityDao.class) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 2 && args[0] instanceof Class<?> entityType && args[1] instanceof Class<?> idType) {
                    return new EntityDaoTypes(entityType, idType);
                }
                throw new IllegalStateException("EntityDao type arguments must be concrete classes: " + pt);
            }
            if (raw instanceof Class<?> rawClass) {
                for (Type next : rawClass.getGenericInterfaces()) {
                    EntityDaoTypes hit = resolveEntityDaoTypes(next);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
            return null;
        }
        if (genericType instanceof Class<?> clazz) {
            for (Type next : clazz.getGenericInterfaces()) {
                EntityDaoTypes hit = resolveEntityDaoTypes(next);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        return lookup
                .findSpecial(
                        declaringClass,
                        method.getName(),
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                        declaringClass
                )
                .bindTo(proxy)
                .invokeWithArguments(args);
    }

    private static boolean hasJdbiSqlAnnotation(Method method) {
        return method.isAnnotationPresent(SqlQuery.class)
                || method.isAnnotationPresent(SqlUpdate.class);
    }

    private record EntityDaoTypes(Class<?> entityType, Class<?> idType) {
    }

    private enum EntityDaoMethodType {
        NONE,
        ENSURE_TABLE,
        INSERT,
        UPDATE_BY_ID,
        DELETE_BY_ID,
        EXISTS_BY_ID,
        FIND_BY_ID,
        QUERY,
        LIST,
        PAGE_QUERY,
        PAGE,
        COUNT,
        UPSERT
    }

    @SuppressWarnings("unchecked")
    private final class EntityDaoDelegate {
        private static final Set<String> RESERVED_METHOD_NAMES = Set.of(
                "insert",
                "updateById",
                "deleteById",
                "existsById",
                "findById",
                "query",
                "list",
                "pageQuery",
                "page",
                "count",
                "upsert"
        );

        private final Class<?> entityType;
        private final SimpleEntityManager entityManager;

        private EntityDaoDelegate(Class<?> entityType) {
            this.entityType = entityType;
            this.entityManager = new DefaultSimpleEntityManager(operations);
            new EntityMetaResolver().resolve(entityType);
        }

        private EntityDaoMethodType resolve(Method method) {
            String name = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            if ("ensureTable".equals(name) && paramTypes.length == 0 && isBooleanReturn(returnType)) {
                return EntityDaoMethodType.ENSURE_TABLE;
            }
            if ("insert".equals(name)
                    && paramTypes.length == 1
                    && returnType != void.class
                    && returnType != Void.class) {
                return EntityDaoMethodType.INSERT;
            }
            if ("updateById".equals(name) && paramTypes.length == 1 && isIntReturn(returnType)) {
                return EntityDaoMethodType.UPDATE_BY_ID;
            }
            if ("deleteById".equals(name) && paramTypes.length == 1 && isIntReturn(returnType)) {
                return EntityDaoMethodType.DELETE_BY_ID;
            }
            if ("existsById".equals(name) && paramTypes.length == 1 && isBooleanReturn(returnType)) {
                return EntityDaoMethodType.EXISTS_BY_ID;
            }
            if ("findById".equals(name)
                    && paramTypes.length == 1
                    && returnType != void.class
                    && returnType != Void.class) {
                return EntityDaoMethodType.FIND_BY_ID;
            }
            if ("query".equals(name)
                    && (paramTypes.length == 2 || (paramTypes.length == 3 && paramTypes[2] == Sort[].class))
                    && paramTypes[0] == Criteria.class
                    && paramTypes[1] == PageRequest.class
                    && java.util.List.class.isAssignableFrom(returnType)) {
                return EntityDaoMethodType.QUERY;
            }
            if ("list".equals(name)
                    && (paramTypes.length == 2 || (paramTypes.length == 3 && paramTypes[2] == Sort[].class))
                    && paramTypes[0] == Criteria.class
                    && paramTypes[1] == PageRequest.class
                    && java.util.List.class.isAssignableFrom(returnType)) {
                return EntityDaoMethodType.LIST;
            }
            if ("pageQuery".equals(name)
                    && (paramTypes.length == 2 || (paramTypes.length == 3 && paramTypes[2] == Sort[].class))
                    && paramTypes[0] == Criteria.class
                    && paramTypes[1] == PageRequest.class
                    && PageResult.class.isAssignableFrom(returnType)) {
                return EntityDaoMethodType.PAGE_QUERY;
            }
            if ("page".equals(name)
                    && (paramTypes.length == 2 || (paramTypes.length == 3 && paramTypes[2] == Sort[].class))
                    && paramTypes[0] == Criteria.class
                    && paramTypes[1] == PageRequest.class
                    && PageResult.class.isAssignableFrom(returnType)) {
                return EntityDaoMethodType.PAGE;
            }
            if ("upsert".equals(name) && paramTypes.length == 1 && isIntReturn(returnType)) {
                return EntityDaoMethodType.UPSERT;
            }
            if ("count".equals(name)
                    && paramTypes.length == 1
                    && paramTypes[0] == Criteria.class
                    && isLongReturn(returnType)) {
                return EntityDaoMethodType.COUNT;
            }
            return EntityDaoMethodType.NONE;
        }

        private void validate(Method method, EntityDaoMethodType resolvedType) {
            if (!RESERVED_METHOD_NAMES.contains(method.getName())) {
                return;
            }
            if (resolvedType != EntityDaoMethodType.NONE) {
                return;
            }
            throw new IllegalStateException(
                    "Invalid EntityDao reserved method signature for '" + method.getName()
                            + "': " + method
                            + ". Expected signature: " + expectedSignature(method.getName())
            );
        }

        private boolean isIntReturn(Class<?> returnType) {
            return returnType == int.class || returnType == Integer.class;
        }

        private boolean isLongReturn(Class<?> returnType) {
            return returnType == long.class || returnType == Long.class;
        }

        private boolean isBooleanReturn(Class<?> returnType) {
            return returnType == boolean.class || returnType == Boolean.class;
        }

        private String expectedSignature(String name) {
            return switch (name) {
                case "ensureTable" -> "boolean ensureTable()";
                case "insert" -> "ID insert(T entity)";
                case "updateById" -> "int updateById(T entity)";
                case "deleteById" -> "int deleteById(ID id)";
                case "existsById" -> "boolean existsById(ID id)";
                case "findById" -> "T findById(ID id)";
                case "query" -> "List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts)";
                case "list" -> "List<T> list(Criteria criteria, PageRequest pageRequest, Sort... sorts)";
                case "pageQuery" -> "PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts)";
                case "page" -> "PageResult<T> page(Criteria criteria, PageRequest pageRequest, Sort... sorts)";
                case "count" -> "long count(Criteria criteria)";
                case "upsert" -> "int upsert(T entity)";
                default -> "see EntityDao<T, ID>";
            };
        }

        private Object invoke(EntityDaoMethodType type, Object[] args) {
            return switch (type) {
                case ENSURE_TABLE -> entityManager.ensureTable((Class<Object>) entityType);
                case INSERT -> entityManager.insert(args[0]);
                case UPDATE_BY_ID -> entityManager.update(args[0]);
                case DELETE_BY_ID -> entityManager.deleteById((Class<Object>) entityType, args[0]);
                case EXISTS_BY_ID -> entityManager.findById((Class<Object>) entityType, args[0]) != null;
                case FIND_BY_ID -> entityManager.findById((Class<Object>) entityType, args[0]);
                case QUERY -> entityManager.query((Class<Object>) entityType, (Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case LIST -> entityManager.query((Class<Object>) entityType, (Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case PAGE_QUERY -> entityManager.pageQuery((Class<Object>) entityType, (Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case PAGE -> entityManager.pageQuery((Class<Object>) entityType, (Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case COUNT -> entityManager.pageQuery((Class<Object>) entityType, (Criteria) args[0], PageRequest.of(1, 1)).getTotal();
                case UPSERT -> entityManager.upsert(args[0]);
                case NONE -> throw new IllegalStateException("Unexpected EntityDao method type");
            };
        }
    }

    private static Sort[] extractSorts(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) {
            return new Sort[0];
        }
        if (args[index] instanceof Sort[] sorts) {
            return sorts;
        }
        if (args[index] instanceof Sort sort) {
            return new Sort[]{sort};
        }
        throw new IllegalArgumentException("Sort arguments must be Sort[]");
    }
}
