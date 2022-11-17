package org.ibatis.extension.annotations;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bind {

    String name() default "";

    Class<?> value();
}
