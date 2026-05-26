package net.ximatai.muyun.database.spring.boot.sql.repository;

import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.internal.EntityDaoTypeResolver;
import net.ximatai.muyun.database.spring.boot.sql.internal.EntityDaoTypeResolver.EntityDaoTypes;

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
        EntityDaoTypes daoTypes = EntityDaoTypeResolver.resolve(repositoryInterface);
        return daoTypes == null ? null : daoTypes.entityType();
    }

    public record RepositoryEntityBinding(Class<?> entityClass, MuYunRepository.AlignTable alignTable) {
    }
}
