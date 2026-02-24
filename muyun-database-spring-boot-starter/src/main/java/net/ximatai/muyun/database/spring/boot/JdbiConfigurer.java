package net.ximatai.muyun.database.spring.boot;

import org.jdbi.v3.core.Jdbi;

@FunctionalInterface
public interface JdbiConfigurer {

    void configure(Jdbi jdbi);
}
