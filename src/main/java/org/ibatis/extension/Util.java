package org.ibatis.extension;

import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.ColumnMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class Util {

    private static final Map<String, Class<?>> MAPPER_CLASS_CACHE_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Method> MAPPER_METHOD_CACHE_MAP = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> CLASS_FIELD_CACHE_MAP = new ConcurrentHashMap<>();

    static Class<?> cachedClass(String className) {
        return computeIfAbsent(MAPPER_CLASS_CACHE_MAP, className, s -> {
            try {
                return Thread.currentThread().getContextClassLoader().loadClass(s);
            } catch (ClassNotFoundException e) {
                throw new Error("Not found class for " + s, e);
            }
        });
    }

    static Method cachedMethod(MappedStatement ms) {
        String id = ms.getId();
        Class<?> clazz = cachedClass(id.substring(0, id.lastIndexOf('.')));
        return computeIfAbsent(MAPPER_METHOD_CACHE_MAP, id.substring(id.lastIndexOf('.') + 1), s -> {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(s)) {
                    return method;
                }
            }
            throw new Error("No such method in " + clazz.getName());
        });
    }

    static Field cachedField(Class<?> clazz, String fieldName) {
        Map<String, Field> fieldMap = CLASS_FIELD_CACHE_MAP.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        return fieldMap.computeIfAbsent(fieldName, s -> {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getName().equals(s)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            throw new Error("No such field " + fieldName + " in " + clazz.getName());
        });
    }

    static <K, V> V computeIfAbsent(Map<K, V> map, K key, Function<K, V> mappingFunction) {
        V value = map.get(key);
        if (value != null) {
            return value;
        }
        return map.computeIfAbsent(key, mappingFunction);
    }

    static void setResultMaps(MappedStatement statement, List<ResultMap> resultMaps) {
        try {
            cachedField(MappedStatement.class, "resultMaps").set(statement, resultMaps);
        } catch (Exception e) {
            throw new Error("Failed setResultMaps: " + e.getMessage(), e);
        }
    }

    static void setKeyGenerator(MappedStatement statement, KeyGenerator keyGenerator) {
        try {
            cachedField(MappedStatement.class, "keyGenerator").set(statement, keyGenerator);
        } catch (Exception e) {
            throw new Error("Failed setKeyGenerator: " + e.getMessage(), e);
        }
    }

    static void setKeyProperty(MappedStatement statement, String keyProperty, String keyColumn) {
        try {
            cachedField(MappedStatement.class, "keyProperties").set(statement, delimitedStringToArray(keyProperty));
            cachedField(MappedStatement.class, "keyColumns").set(statement, delimitedStringToArray(keyColumn));
        } catch (Exception e) {
            throw new Error("Failed setKeyProperty: " + e.getMessage(), e);
        }
    }

    private static String[] delimitedStringToArray(String in) {
        if (in == null || in.trim().length() == 0) {
            return null;
        } else {
            return in.split(",");
        }
    }

    static String toTableName(Class<?> clazz) {
        Bind bind;
        if ((bind = clazz.getAnnotation(Bind.class)) == null) {
            throw new IllegalStateException("Missing @Bind: " + clazz.getName());
        }
        String mapping = bind.name();
        if (!mapping.isEmpty()) {
            return mapping;
        }
        String name = bind.value().getSimpleName();
        return name.replaceAll("([A-Z]+)", "_$0").toLowerCase().substring(1);
    }

    static String toColumnName(Field field) {

        String name = field.getName();
        ColumnMapping columnMapping;
        if ((columnMapping = field.getAnnotation(ColumnMapping.class)) != null) {
            name = columnMapping.value();
        }
        return name;
    }

    static Map<Field, ColumnMapping> toColumnMappingMap(Bind bind) {
        Map<Field, ColumnMapping> columnMappingMap = new HashMap<>();
        for (Field field : bind.value().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                ColumnMapping columnMapping;
                if ((columnMapping = field.getAnnotation(ColumnMapping.class)) != null) {
                    columnMappingMap.put(field, columnMapping);
                }
            }
        }
        return columnMappingMap;
    }

    static String escape(String text) {
        return String.format("`%s`", text);
    }
}
