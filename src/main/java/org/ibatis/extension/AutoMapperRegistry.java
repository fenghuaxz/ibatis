package org.ibatis.extension;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.Id;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
final class AutoMapperRegistry extends MapperRegistry {

    private final Driver driver;

    public AutoMapperRegistry(Configuration config, Driver driver) {
        super(config);
        this.driver = driver;
    }

    @Override
    public <T> T getMapper(Class<T> type, SqlSession session) {
        synchronized (this) {
            if (!hasMapper(type)) {
                Bind bind;
                if ((bind = type.getAnnotation(Bind.class)) == null) {
                    throw new IllegalStateException("Missing @Bind: " + type.getName());
                }
                replaceTags(bind, type);
                driver.generateTable(bind, type, session);
                addMapper(type);
            }
            return super.getMapper(type, session);
        }
    }

    private Map<String, Object> memberValuesMap(Annotation annotation) {
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(annotation);
            Field field = handler.getClass().getDeclaredField("memberValues");
            field.setAccessible(true);
            return (Map<String, Object>) field.get(handler);
        } catch (Exception e) {
            throw new Error(e.getMessage(), e);
        }
    }

    private void replaceTags(Bind bind, Class<?> clazz) {
        String tableName = driver.tableName(clazz);

        for (Method method : clazz.getMethods()) {
            Annotation a;
            if ((a = method.getAnnotation(Insert.class)) != null || (a = method.getAnnotation(Delete.class)) != null ||
                    (a = method.getAnnotation(Update.class)) != null || (a = method.getAnnotation(Select.class)) != null) {
                Map<String, Object> memberValues = memberValuesMap(a);

                String sql = ((String[]) memberValues.get("value"))[0];

                //省略符处理
                if (Jdbc.OMIT.equals(sql)) {

                    if (a instanceof Insert) {
                        Field[] fields = bind.value().getDeclaredFields();
                        List<String> params = new ArrayList<>();
                        Field idField = null;
                        for (Field field : fields) {
                            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                                continue;
                            }
                            if (field.getAnnotation(Id.class) != null) {
                                idField = field;
                                continue;
                            }
                            params.add("#{" + field.getName() + "}");
                        }
                        String temp = String.valueOf(params);
                        temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
                        sql = String.format("INSERT INTO @table(%s) VALUES(%s)", temp.replaceAll("(?:#\\{|})", ""), temp);

                        if (idField != null) {
                            try {
                                //注入注解 回写id
                                Field declaredAnnotationsField = Executable.class.getDeclaredField("declaredAnnotations");
                                declaredAnnotationsField.setAccessible(true);
                                Map<Class<? extends Annotation>, Annotation> declaredAnnotations = (Map<Class<? extends Annotation>, Annotation>) declaredAnnotationsField.get(method);
                                Class<?> invokeHandlerClass = Proxy.getInvocationHandler(a).getClass();
                                Constructor<?> constructor = invokeHandlerClass.getDeclaredConstructor(Class.class, Map.class);
                                constructor.setAccessible(true);
                                Map<String, Object> mMemberValues = new HashMap<>();
                                mMemberValues.put("useCache", true);
                                mMemberValues.put("flushCache", Options.FlushCachePolicy.DEFAULT);
                                mMemberValues.put("resultSetType", ResultSetType.DEFAULT);
                                mMemberValues.put("statementType", StatementType.PREPARED);
                                mMemberValues.put("fetchSize", -1);
                                mMemberValues.put("timeout", -1);
                                mMemberValues.put("resultSets", "");
                                mMemberValues.put("useGeneratedKeys", true);
                                mMemberValues.put("keyProperty", idField.getName());
                                mMemberValues.put("keyColumn", idField.getName());
                                mMemberValues.put("databaseId", "");
                                InvocationHandler handler = (InvocationHandler) constructor.newInstance(Options.class, mMemberValues);
                                Annotation annotation = (Annotation) Proxy.newProxyInstance(Options.class.getClassLoader(), new Class[]{Options.class}, handler);
                                declaredAnnotations.put(Options.class, annotation);
                            } catch (Exception e) {
                                throw new Error("Failed to insert annotation.", e);
                            }
                        }
                    }

                    if (a instanceof Update) {
                        Class<?> cls = bind.value();
                        Field[] fields = cls.getDeclaredFields();
                        String pk = null;
                        List<String> params = new ArrayList<>();
                        for (Field field : fields) {
                            String name = field.getName();
                            if (field.getAnnotation(Id.class) != null) {
                                pk = name + "=#{" + name + "}";
                                continue;
                            }
                            params.add(name + "=#{" + name + "}");
                        }

                        if (pk == null) {
                            throw new IllegalStateException("Missing @Id: " + cls.getName());
                        }

                        String temp = String.valueOf(params);
                        temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
                        sql = String.format("UPDATE @table SET %s WHERE %s", temp, pk);
                    }
                }

                //如果是查询语句
                if (a instanceof Select) {
                    //如果返回值数量不是数组 那就补上限制只需要一个查询结果
                    if (!method.getReturnType().isArray() && !sql.matches(".*?limit\\s+1")) {
                        sql = sql.concat(" limit 1");
                    }
                }

                memberValues.put("value", new String[]{sql.replace("@table", tableName)});
            }
        }
    }
}
