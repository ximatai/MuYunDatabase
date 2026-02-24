package net.ximatai.muyun.database.spring.boot.sql.annotation;

import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MuYunRepositoryRegistrar.class)
public @interface EnableMuYunRepositories {

    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};
}
