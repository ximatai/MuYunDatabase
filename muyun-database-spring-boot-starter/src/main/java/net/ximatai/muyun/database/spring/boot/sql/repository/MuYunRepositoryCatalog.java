package net.ximatai.muyun.database.spring.boot.sql.repository;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MuYunRepositoryCatalog {

    private final Set<String> repositoryInterfaceNames;

    public MuYunRepositoryCatalog(Set<String> repositoryInterfaceNames) {
        this.repositoryInterfaceNames = repositoryInterfaceNames == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(repositoryInterfaceNames));
    }

    public List<RepositoryEntityBinding> resolveEntityBindings(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        Set<RepositoryEntityBinding> bindings = new LinkedHashSet<>();
        for (String interfaceName : repositoryInterfaceNames) {
            try {
                Class<?> repositoryInterface = Class.forName(interfaceName, false, classLoader);
                Class<?> entityType = resolveEntityType(repositoryInterface);
                if (entityType != null) {
                    MuYunRepository annotation = repositoryInterface.getAnnotation(MuYunRepository.class);
                    MuYunRepository.AlignTable alignTable = annotation == null
                            ? MuYunRepository.AlignTable.DEFAULT
                            : annotation.alignTable();
                    bindings.add(new RepositoryEntityBinding(entityType, alignTable));
                }
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Failed to load repository interface: " + interfaceName, ex);
            }
        }
        return List.copyOf(bindings);
    }

    private Class<?> resolveEntityType(Class<?> repositoryInterface) {
        for (Type genericInterface : repositoryInterface.getGenericInterfaces()) {
            Class<?> hit = resolveEntityType(genericInterface);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Class<?> resolveEntityType(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw == EntityDao.class) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 2 && args[0] instanceof Class<?> entityType) {
                    return entityType;
                }
                throw new IllegalStateException("EntityDao type arguments must be concrete classes: " + pt);
            }
            if (raw instanceof Class<?> rawClass) {
                for (Type next : rawClass.getGenericInterfaces()) {
                    Class<?> hit = resolveEntityType(next);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
            return null;
        }
        if (genericType instanceof Class<?> clazz) {
            for (Type next : clazz.getGenericInterfaces()) {
                Class<?> hit = resolveEntityType(next);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    public record RepositoryEntityBinding(Class<?> entityClass, MuYunRepository.AlignTable alignTable) {
    }
}
