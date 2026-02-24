package net.ximatai.muyun.database.spring.boot.sql;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.CrudRepository;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.EntityMapper;
import net.ximatai.muyun.database.core.orm.EntityMetaResolver;
import net.ximatai.muyun.database.core.orm.PageResult;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.SimpleOrm;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Delete;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Insert;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Param;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Select;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Update;
import org.springframework.core.env.Environment;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MuYunRepositoryFactory {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("([#$])\\{([^}]+)}");

    private final IDatabaseOperations<?> operations;
    private final Environment environment;
    private final EntityMetaResolver entityMetaResolver = new EntityMetaResolver();

    public MuYunRepositoryFactory(IDatabaseOperations<?> operations, Environment environment) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.environment = Objects.requireNonNull(environment, "environment");
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
        private final Map<Method, MethodPlan> plans = new ConcurrentHashMap<>();
        private final EntityDaoDelegate entityDaoDelegate;

        private DaoInvocationHandler(Class<?> daoType) {
            this.daoType = daoType;
            this.entityDaoDelegate = resolveEntityDaoDelegate(daoType);
            // fail fast: validate all abstract methods at creation time
            for (Method method : daoType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                plans.put(method, buildPlan(method));
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            if (method.isDefault()) {
                return invokeDefault(proxy, method, args == null ? new Object[0] : args);
            }
            MethodPlan plan = plans.computeIfAbsent(method, this::buildPlan);
            if (plan.entityDaoMethodType() != EntityDaoMethodType.NONE) {
                return entityDaoDelegate.invoke(plan.entityDaoMethodType(), args == null ? new Object[0] : args);
            }
            Map<String, Object> argMap = plan.bindArgs(args == null ? new Object[0] : args);
            RenderedSql rendered = renderSql(plan.sqlTemplate(), argMap);
            return execute(plan, rendered);
        }

        private MethodPlan buildPlan(Method method) {
            EntityDaoMethodType entityDaoMethodType = entityDaoDelegate == null
                    ? EntityDaoMethodType.NONE
                    : entityDaoDelegate.resolve(method);
            if (entityDaoDelegate != null) {
                entityDaoDelegate.validate(method, entityDaoMethodType);
            }
            if (entityDaoMethodType != EntityDaoMethodType.NONE) {
                if (hasSqlAnnotation(method)) {
                    throw new IllegalStateException(
                            "CRUD method is reserved by EntityDao and must not use @Select/@Insert/@Update/@Delete: " + method
                    );
                }
                return MethodPlan.forEntityDao(method, entityDaoMethodType);
            }

            QueryType type;
            String sql = null;

            Select select = method.getAnnotation(Select.class);
            if (select != null) {
                type = QueryType.SELECT;
                sql = select.value();
            } else {
                Insert insert = method.getAnnotation(Insert.class);
                Update update = method.getAnnotation(Update.class);
                Delete delete = method.getAnnotation(Delete.class);
                if (insert != null) {
                    type = QueryType.UPDATE;
                    sql = insert.value();
                } else if (update != null) {
                    type = QueryType.UPDATE;
                    sql = update.value();
                } else if (delete != null) {
                    type = QueryType.UPDATE;
                    sql = delete.value();
                } else {
                    if (entityDaoDelegate != null) {
                        throw new IllegalStateException("Non-CRUD method must use @Select/@Insert/@Update/@Delete: " + method);
                    }
                    throw new IllegalStateException("Method must use @Select/@Insert/@Update/@Delete: " + method);
                }
            }

            if (!daoType.isAnnotationPresent(MuYunRepository.class)) {
                throw new IllegalStateException("DAO interface must be annotated with @MuYunRepository: " + daoType.getName());
            }

            Set<String> tokenRoots = parseBindTokenRoots(sql);
            return new MethodPlan(type, sql, method, tokenRoots);
        }

        private Object execute(MethodPlan plan, RenderedSql rendered) {
            if (plan.type() == QueryType.UPDATE) {
                int affected = operations.update(rendered.sql(), rendered.params());
                return castUpdateResult(affected, plan.method().getReturnType());
            }
            return executeSelect(plan, rendered);
        }

        private Object executeSelect(MethodPlan plan, RenderedSql rendered) {
            Class<?> returnType = plan.method().getReturnType();
            if (returnType == List.class || returnType == Set.class) {
                Class<?> elementType = plan.collectionElementType();
                List<Map<String, Object>> rows = operations.query(rendered.sql(), rendered.params());
                List<Object> mapped = mapRows(rows, elementType);
                if (returnType == Set.class) {
                    return new LinkedHashSet<>(mapped);
                }
                return mapped;
            }

            Map<String, Object> row = operations.row(rendered.sql(), rendered.params());
            if (row == null) {
                return null;
            }
            return mapSingle(row, returnType);
        }

        private Object castUpdateResult(int affected, Class<?> returnType) {
            if (returnType == void.class || returnType == Void.class) {
                return null;
            }
            if (returnType == int.class || returnType == Integer.class) {
                return affected;
            }
            if (returnType == long.class || returnType == Long.class) {
                return (long) affected;
            }
            if (returnType == boolean.class || returnType == Boolean.class) {
                return affected > 0;
            }
            throw new IllegalStateException("Unsupported update return type: " + returnType.getName());
        }

        private List<Object> mapRows(List<Map<String, Object>> rows, Class<?> elementType) {
            List<Object> list = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                list.add(mapSingle(row, elementType));
            }
            return list;
        }

        private Object mapSingle(Map<String, Object> row, Class<?> targetType) {
            if (targetType == Map.class) {
                return row;
            }
            if (isScalar(targetType)) {
                Object first = null;
                if (!row.isEmpty()) {
                    first = row.values().iterator().next();
                }
                return convertScalar(first, targetType);
            }
            return EntityMapper.fromMap(entityMetaResolver.resolve(targetType), row, targetType);
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

    private RenderedSql renderSql(String template, Map<String, Object> methodArgs) {
        Map<String, Object> bindParams = new LinkedHashMap<>();
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        int bindSeq = 0;

        while (matcher.find()) {
            String tokenType = matcher.group(1);
            String expression = matcher.group(2).trim();
            Object value = resolveValue(expression, methodArgs);

            if ("$".equals(tokenType)) {
                if (value == null) {
                    throw new IllegalArgumentException("Missing value for inline token ${" + expression + "}");
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
                continue;
            }

            if (value instanceof Iterable<?> iterable) {
                bindSeq = appendSequenceBindParams(expression, iterable.iterator(), bindParams, matcher, buffer, bindSeq);
            } else if (isExpandableArray(value)) {
                bindSeq = appendSequenceBindParams(expression, arrayIterator(value), bindParams, matcher, buffer, bindSeq);
            } else {
                String key = "__p" + bindSeq++;
                bindParams.put(key, normalizeBindableValue(value));
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(":" + key));
            }
        }

        matcher.appendTail(buffer);
        return new RenderedSql(buffer.toString(), bindParams);
    }

    private int appendSequenceBindParams(String expression,
                                         Iterator<?> iterator,
                                         Map<String, Object> bindParams,
                                         Matcher matcher,
                                         StringBuffer buffer,
                                         int bindSeq) {
        List<String> holders = new ArrayList<>();
        while (iterator.hasNext()) {
            String key = "__p" + bindSeq++;
            bindParams.put(key, normalizeBindableValue(iterator.next()));
            holders.add(":" + key);
        }
        if (holders.isEmpty()) {
            throw new IllegalArgumentException("Empty collection/array is not allowed for token #{" + expression + "}");
        }
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.join(", ", holders)));
        return bindSeq;
    }

    private static boolean isExpandableArray(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return false;
        }
        // Treat byte array as scalar to preserve common BLOB binding semantics.
        return type != byte[].class && type != Byte[].class;
    }

    private static Iterator<Object> arrayIterator(Object array) {
        int length = Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            values.add(Array.get(array, i));
        }
        return values.iterator();
    }

    private Object normalizeBindableValue(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private Object resolveValue(String expression, Map<String, Object> methodArgs) {
        if (expression.contains(".")) {
            Object fromArgs = resolvePath(methodArgs, expression);
            if (fromArgs != null) {
                return fromArgs;
            }
            String env = environment.getProperty(expression);
            if (env != null) {
                return env;
            }
            return null;
        }

        if (methodArgs.containsKey(expression)) {
            return methodArgs.get(expression);
        }
        if (methodArgs.containsKey("value")) {
            Object value = methodArgs.get("value");
            Object nested = resolvePath(value, expression);
            if (nested != null) {
                return nested;
            }
            if (methodArgs.size() <= 2) {
                return value;
            }
        }
        String env = environment.getProperty(expression);
        if (env != null) {
            return env;
        }
        return null;
    }

    private Object resolvePath(Object root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            current = readPart(current, part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object readPart(Object source, String part) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            if (map.containsKey(part)) {
                return map.get(part);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (part.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
        }
        try {
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(source.getClass()).getPropertyDescriptors();
            for (PropertyDescriptor descriptor : descriptors) {
                if (descriptor.getReadMethod() != null && descriptor.getName().equals(part)) {
                    return descriptor.getReadMethod().invoke(source);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Set<String> parseBindTokenRoots(String sql) {
        Set<String> roots = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tokenType = matcher.group(1);
            if (!"#".equals(tokenType)) {
                continue;
            }
            String expression = matcher.group(2).trim();
            int dot = expression.indexOf('.');
            roots.add(dot > 0 ? expression.substring(0, dot) : expression);
        }
        return roots;
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

    private static boolean isScalar(Class<?> type) {
        return type == String.class
                || Number.class.isAssignableFrom(type)
                || type.isPrimitive()
                || type == Boolean.class
                || type == Character.class
                || type.isEnum();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertScalar(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return toInt(value);
        }
        if (targetType == Long.class || targetType == long.class) {
            return toLong(value);
        }
        if (targetType == Double.class || targetType == double.class) {
            return toDouble(value);
        }
        if (targetType == Float.class || targetType == float.class) {
            return toFloat(value);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return toBoolean(value);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) targetType, String.valueOf(value));
        }
        return value;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(requireText(value));
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(requireText(value));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(requireText(value));
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(requireText(value));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = requireText(value).toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "y".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "n".equals(text) || "no".equals(text)) {
            return false;
        }
        return Boolean.parseBoolean(text);
    }

    private static String requireText(Object value) {
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert blank value");
        }
        return text;
    }

    private enum QueryType {
        SELECT,
        UPDATE
    }

    private record RenderedSql(String sql, Map<String, Object> params) {
    }

    private static final class MethodPlan {
        private final QueryType type;
        private final String sqlTemplate;
        private final Method method;
        private final Class<?> collectionElementType;
        private final List<ArgumentPlan> arguments;
        private final EntityDaoMethodType entityDaoMethodType;

        private MethodPlan(QueryType type, String sqlTemplate, Method method, Set<String> tokenRoots) {
            this.type = type;
            this.sqlTemplate = sqlTemplate;
            this.method = method;
            this.collectionElementType = resolveCollectionElementType(method);
            this.arguments = resolveArguments(method, tokenRoots);
            this.entityDaoMethodType = EntityDaoMethodType.NONE;
        }

        private MethodPlan(Method method, EntityDaoMethodType entityDaoMethodType) {
            this.type = QueryType.SELECT;
            this.sqlTemplate = "";
            this.method = method;
            this.collectionElementType = resolveCollectionElementType(method);
            this.arguments = List.of();
            this.entityDaoMethodType = entityDaoMethodType;
        }

        static MethodPlan forEntityDao(Method method, EntityDaoMethodType entityDaoMethodType) {
            return new MethodPlan(method, entityDaoMethodType);
        }

        private static List<ArgumentPlan> resolveArguments(Method method, Set<String> tokenRoots) {
            Parameter[] parameters = method.getParameters();
            List<ArgumentPlan> plans = new ArrayList<>(parameters.length);

            String singleName = null;
            if (parameters.length == 1 && tokenRoots.size() == 1) {
                singleName = tokenRoots.iterator().next();
            }

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Param param = parameter.getAnnotation(Param.class);
                String name;
                if (param != null && !param.value().isBlank()) {
                    name = param.value();
                } else if (singleName != null) {
                    name = singleName;
                } else if ("arg0".equals(parameter.getName()) || parameter.getName().startsWith("arg")) {
                    name = "p" + i;
                } else {
                    name = parameter.getName();
                }
                plans.add(new ArgumentPlan(name));
            }
            return plans;
        }

        private static Class<?> resolveCollectionElementType(Method method) {
            Class<?> raw = method.getReturnType();
            if (raw != List.class && raw != Set.class) {
                return raw;
            }
            Type generic = method.getGenericReturnType();
            if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return Map.class;
        }

        private Map<String, Object> bindArgs(Object[] args) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (args.length != arguments.size()) {
                throw new IllegalArgumentException("Parameter count mismatch for method: " + method);
            }
            for (int i = 0; i < args.length; i++) {
                map.put(arguments.get(i).name(), args[i]);
            }
            if (args.length == 1 && !map.containsKey("value")) {
                map.put("value", args[0]);
            }
            return map;
        }

        QueryType type() {
            return type;
        }

        String sqlTemplate() {
            return sqlTemplate;
        }

        Method method() {
            return method;
        }

        Class<?> collectionElementType() {
            return collectionElementType;
        }

        EntityDaoMethodType entityDaoMethodType() {
            return entityDaoMethodType;
        }
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
        private final CrudRepository<Object, Object> repository;
        private final SimpleEntityManager entityManager;
        private final Class<?> entityType;

        private EntityDaoDelegate(Class<?> entityType) {
            this.entityType = entityType;
            this.entityManager = new DefaultSimpleEntityManager(operations);
            this.repository = (CrudRepository<Object, Object>) SimpleOrm.repository(
                    (Class<Object>) entityType,
                    entityManager
            );
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
                    && List.class.isAssignableFrom(returnType)) {
                return EntityDaoMethodType.QUERY;
            }
            if ("list".equals(name)
                    && (paramTypes.length == 2 || (paramTypes.length == 3 && paramTypes[2] == Sort[].class))
                    && paramTypes[0] == Criteria.class
                    && paramTypes[1] == PageRequest.class
                    && List.class.isAssignableFrom(returnType)) {
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
                case INSERT -> repository.insert(args[0]);
                case UPDATE_BY_ID -> repository.update(args[0]);
                case DELETE_BY_ID -> repository.deleteById(args[0]);
                case EXISTS_BY_ID -> repository.findById(args[0]) != null;
                case FIND_BY_ID -> repository.findById(args[0]);
                case QUERY -> repository.query((Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case LIST -> repository.query((Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case PAGE_QUERY -> repository.pageQuery((Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case PAGE -> repository.pageQuery((Criteria) args[0], (PageRequest) args[1], extractSorts(args, 2));
                case COUNT -> repository.pageQuery((Criteria) args[0], PageRequest.of(1, 1)).getTotal();
                case UPSERT -> repository.upsert(args[0]);
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

    private static boolean hasSqlAnnotation(Method method) {
        return method.isAnnotationPresent(Select.class)
                || method.isAnnotationPresent(Insert.class)
                || method.isAnnotationPresent(Update.class)
                || method.isAnnotationPresent(Delete.class);
    }

    private record ArgumentPlan(String name) {
    }
}
