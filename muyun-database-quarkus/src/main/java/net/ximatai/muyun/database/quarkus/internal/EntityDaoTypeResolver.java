package net.ximatai.muyun.database.quarkus.internal;

import net.ximatai.muyun.database.core.orm.EntityDao;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

public final class EntityDaoTypeResolver {

    private EntityDaoTypeResolver() {
    }

    public static EntityDaoTypes resolve(Class<?> daoType) {
        for (Type genericInterface : daoType.getGenericInterfaces()) {
            EntityDaoTypes hit = resolve(genericInterface, Map.of());
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static EntityDaoTypes resolve(Type genericType, Map<TypeVariable<?>, Type> bindings) {
        if (genericType instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> rawClass) {
                Map<TypeVariable<?>, Type> nextBindings = new HashMap<>(bindings);
                TypeVariable<?>[] variables = rawClass.getTypeParameters();
                Type[] args = pt.getActualTypeArguments();
                for (int i = 0; i < variables.length && i < args.length; i++) {
                    nextBindings.put(variables[i], resolveType(args[i], bindings));
                }

                if (rawClass == EntityDao.class) {
                    Type entityArg = resolveType(args[0], bindings);
                    Type idArg = resolveType(args[1], bindings);
                    if (entityArg instanceof Class<?> entityType && idArg instanceof Class<?> idType) {
                        return new EntityDaoTypes(entityType, idType);
                    }
                    throw new IllegalStateException("EntityDao type arguments must be concrete classes: " + pt);
                }

                for (Type next : rawClass.getGenericInterfaces()) {
                    EntityDaoTypes hit = resolve(next, nextBindings);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
            return null;
        }
        if (genericType instanceof Class<?> clazz) {
            for (Type next : clazz.getGenericInterfaces()) {
                EntityDaoTypes hit = resolve(next, bindings);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            return bindings.getOrDefault(variable, variable);
        }
        return type;
    }

    public record EntityDaoTypes(Class<?> entityType, Class<?> idType) {
    }
}
