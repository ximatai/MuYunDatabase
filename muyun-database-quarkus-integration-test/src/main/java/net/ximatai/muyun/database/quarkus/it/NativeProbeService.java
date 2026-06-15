package net.ximatai.muyun.database.quarkus.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NativeProbeService {

    @Inject
    NativeProbeResource.NativeProbeRepository repository;

    @Transactional
    public void insertAndRollback(String id) {
        repository.insert(new NativeProbeResource.NativeProbeEntity(id, "tx"));
        repository.rename(id, "tx-sql");
        throw new IllegalStateException("force rollback");
    }
}
