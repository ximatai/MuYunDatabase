package net.ximatai.muyun.database.samples.quarkusminimal;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/demo")
public class DemoResource {
    @Inject
    DemoUserRepository repository;

    @Inject
    DemoUserService service;

    @GET
    @Path("/repository")
    public String repository() {
        String id = "demo-1";
        repository.deleteById(id);
        repository.insert(new DemoUser(id, "created"));
        repository.rename(id, "renamed");
        String name = repository.findViaSql(id).getName();
        repository.deleteById(id);
        return "repository:" + name;
    }

    @GET
    @Path("/transaction")
    public String transaction() {
        String id = "demo-tx-1";
        repository.deleteById(id);
        try {
            service.insertAndRollback(id);
        } catch (IllegalStateException expected) {
            // expected rollback demo
        }
        return repository.findById(id) == null ? "transaction:rolled-back" : "transaction:leaked";
    }
}
