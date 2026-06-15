package net.ximatai.muyun.database.samples.quarkusminimal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DemoUserService {
    @Inject
    DemoUserRepository repository;

    @Transactional
    public void insertAndRollback(String id) {
        repository.insert(new DemoUser(id, "tx-created"));
        repository.rename(id, "tx-renamed");
        throw new IllegalStateException("rollback demo");
    }
}
