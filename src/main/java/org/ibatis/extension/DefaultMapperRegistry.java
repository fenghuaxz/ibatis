package org.ibatis.extension;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.binding.MapperProxyFactory;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;

import java.lang.reflect.Field;
import java.util.*;

final class DefaultMapperRegistry extends MapperRegistry {

    private final Driver driver;
    private final Configuration config;

    public DefaultMapperRegistry(Configuration config, Driver driver) {
        super(config);
        this.config = config;
        this.driver = driver;
        config.setDefaultScriptingLanguage(DefaultLanguageDriver.class);
    }

    @Override
    public <T> T getMapper(Class<T> type, SqlSession session) {
        synchronized (this) {
            if (!hasMapper(type)) {
                Bind bind;
                if ((bind = type.getAnnotation(Bind.class)) == null) {
                    throw new IllegalStateException("Missing @Bind: " + type.getName());
                }
                driver.generateTable(bind, type, session);
                addMapper(type);
            }
            return super.getMapper(type, session);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, MapperProxyFactory<?>> knownMappers() {
        try {
            Field field = MapperRegistry.class.getDeclaredField("knownMappers");
            field.setAccessible(true);
            return (Map<Class<?>, MapperProxyFactory<?>>) field.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> void addMapper(Class<T> type) {

        if (type.isInterface()) {
            if (hasMapper(type)) {
                throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
            }

            Map<Class<?>, MapperProxyFactory<?>> knownMappers = knownMappers();

            boolean loadCompleted = false;
            try {
                knownMappers.put(type, new MapperProxyFactory<>(type));
                new DefaultMapperAnnotationBuilder(config, type, driver).parse();
                loadCompleted = true;
            } finally {
                if (!loadCompleted) {
                    knownMappers.remove(type);
                }
            }
        }
    }
}
