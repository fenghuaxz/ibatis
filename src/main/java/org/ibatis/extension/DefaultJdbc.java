package org.ibatis.extension;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Proxy;

final class DefaultJdbc implements Jdbc {

    private final SqlSessionFactory sqlSessionFactory;

    DefaultJdbc(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> clazz) {
        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalStateException("Mapper cannot be null and must be an interface!");
        }
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (proxy, method, args) -> {
            SqlSession session = sqlSessionFactory.openSession();

            Object obj = session.getMapper(clazz);

            try {
                Object val = method.invoke(obj, args);
                if (!method.isAnnotationPresent(Select.class)) {
                    session.commit();
                }
                return val;
            } catch (Throwable ex) {
                if (method.isAnnotationPresent(Insert.class) || method.isAnnotationPresent(Delete.class) || method.isAnnotationPresent(Update.class)) {
                    session.rollback();
                }
                throw ex;
            } finally {
                session.close();
            }
        });
    }
}
