package org.ibatis.extension.annotations;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DateFormat {

    String value();
}
