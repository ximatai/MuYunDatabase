package net.ximatai.muyun.database.quarkus;

import org.jdbi.v3.core.Jdbi;

@FunctionalInterface
public interface MuYunJdbiConfigurer {

    void configure(Jdbi jdbi);
}
