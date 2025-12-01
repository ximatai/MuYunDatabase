package net.ximatai.muyun.database.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Default {

    boolean unset() default false;

    boolean nullVal() default false;

    String varchar() default "";

    TrueOrFalse bool() default TrueOrFalse.UNSET;

    long number() default Long.MIN_VALUE;

    double decimal() default Double.MIN_VALUE;

    String function() default "";

    String express() default "";

}

