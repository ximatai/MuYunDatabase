package net.ximatai.muyun.database.quarkus;

import io.quarkus.runtime.annotations.Recorder;

import java.util.function.Supplier;

@Recorder
public class MuYunDatabaseRecorder {

    public Supplier<Object> repositorySupplier(String repositoryClassName) {
        return new MuYunRepositorySupplier(repositoryClassName);
    }
}
