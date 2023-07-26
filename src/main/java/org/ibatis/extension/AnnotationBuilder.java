package org.ibatis.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class AnnotationBuilder<T extends Annotation> {

    private final Class<T> type;
    private final Map<String, Object> memberValues = new HashMap<>();

    AnnotationBuilder(Class<T> type) {
        this.type = type;
    }

    public AnnotationBuilder<T> putMember(String name, Object value) {
        this.memberValues.put(name, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public T build() {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new Handler(this));
    }

    private static class Handler implements InvocationHandler {

        private final AnnotationBuilder<?> builder;

        private Handler(AnnotationBuilder<?> builder) {
            this.builder = builder;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            if ("annotationType".equals(method.getName())) {
                return builder.type;
            }

            return builder.memberValues.get(method.getName());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("@").append(builder.type.getName()).append("(");
            for (Map.Entry<String, Object> entry : builder.memberValues.entrySet()) {
                boolean isArray = entry.getValue().getClass().isArray();
                sb.append(entry.getKey()).append("=").append(isArray ? Arrays.toString((Object[]) entry.getValue()) : entry.getValue()).append(", ");
            }
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
