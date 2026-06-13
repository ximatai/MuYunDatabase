package net.ximatai.muyun.database.quarkus;

import io.quarkus.arc.Arc;

import java.util.Objects;
import java.util.function.Supplier;

public class MuYunRepositorySupplier implements Supplier<Object> {

    private final String repositoryClassName;

    public MuYunRepositorySupplier(String repositoryClassName) {
        this.repositoryClassName = Objects.requireNonNull(repositoryClassName, "repositoryClassName must not be null");
    }

    @Override
    public Object get() {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> repositoryType = Class.forName(repositoryClassName, false, classLoader);
            MuYunRepositoryFactory factory = Arc.container().instance(MuYunRepositoryFactory.class).get();
            return factory.create(repositoryType);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("MuYun repository class not found: " + repositoryClassName, ex);
        }
    }
}
