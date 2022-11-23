package org.ibatis.extension;

import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.mapping.FetchType;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.ColumnMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

@Bind(Object.class)
final class Utils {

    @SuppressWarnings("unchecked")
    static Map<String, Object> getMemberValuesMap(Annotation annotation) {
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(annotation);
            Field field = handler.getClass().getDeclaredField("memberValues");
            field.setAccessible(true);
            return (Map<String, Object>) field.get(handler);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new Error(e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Annotation> T create(Class<T> clazz, Map<String, Object> memberValuesMap) {
        try {
            Class<?> invokeHandlerClass = Proxy.getInvocationHandler(Utils.class.getAnnotation(Bind.class)).getClass();
            Constructor<?> constructor = invokeHandlerClass.getDeclaredConstructor(Class.class, Map.class);
            constructor.setAccessible(true);
            InvocationHandler handler = (InvocationHandler) constructor.newInstance(clazz, memberValuesMap);
            return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
        } catch (Exception e) {
            throw new Error("Failed to create annotation.", e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Annotation> void add(Method method, Class<T> clazz, T annotation) {
        try {
            Field declaredAnnotationsField = Executable.class.getDeclaredField("declaredAnnotations");
            declaredAnnotationsField.setAccessible(true);
            Map<Class<? extends Annotation>, Annotation> declaredAnnotations = (Map<Class<? extends Annotation>, Annotation>) declaredAnnotationsField.get(method);
            declaredAnnotations.put(clazz, annotation);
        } catch (Exception e) {
            throw new Error("Failed to insert annotation.", e);
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

    static Map<String, String> toColumnMappingMap(Bind bind) {
        Map<String, String> columnMapping = new HashMap<>();

        for (Field field : bind.value().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                String originalName = field.getName();
                String columnName = Utils.toColumnName(field);
                if (!originalName.equals(columnName)) {
                    columnMapping.put(originalName, columnName);
                }
            }
        }
        return columnMapping;
    }

    static String escape(String text) {
        return String.format("`%s`", text);
    }

    static One one() {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("columnPrefix", "");
        valuesMap.put("resultMap", "");
        valuesMap.put("select", "");
        valuesMap.put("fetchType", FetchType.DEFAULT);
        return create(One.class, valuesMap);
    }

    static Many many() {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("columnPrefix", "");
        valuesMap.put("resultMap", "");
        valuesMap.put("select", "");
        valuesMap.put("fetchType", FetchType.DEFAULT);
        return create(Many.class, valuesMap);
    }
}
