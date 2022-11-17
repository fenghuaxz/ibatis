package org.ibatis.extension.annotations;

import java.lang.annotation.*;
import java.sql.Timestamp;


@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeMapping {

    String value();

    enum Constant {
        INT(Integer.TYPE, "INT"),
        LONG(Long.TYPE, "BIGINT"),
        FLOAT(Float.TYPE, "FLOAT"),
        DOUBLE(Double.TYPE, "DOUBLE"),
        BOOLEAN(Boolean.TYPE, "BOOLEAN"),
        CHAR(Character.TYPE, "CHAR(1)"),
        TIMESTAMP(Timestamp.class, "TIMESTAMP"),
        BLOB(byte[].class, "LONGBLOB"),
        STRING(String.class, "TEXT");

        private final Class<?> javaType;
        private final String jdbcType;

        Constant(Class<?> javaType, String jdbcType) {
            this.javaType = javaType;
            this.jdbcType = jdbcType;
        }

        public static String getMapping(Class<?> javaType) {
            for (Constant constant : values()) {
                if (javaType == constant.javaType) {
                    return constant.jdbcType;
                }
            }
            throw new IllegalStateException("Type is not mapped: " + javaType.getName());
        }
    }
}
