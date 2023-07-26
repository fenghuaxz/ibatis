package org.ibatis.extension;

import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.TextSqlNode;
import org.apache.ibatis.scripting.xmltags.XMLScriptBuilder;
import org.apache.ibatis.session.Configuration;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.ColumnMapping;
import org.ibatis.extension.annotations.Id;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DefaultLanguageDriver implements LanguageDriver {

    @Override
    public ParameterHandler createParameterHandler(MappedStatement ms, Object parameterObject, BoundSql boundSql) {

        Method method = Util.cachedMethod(ms);

        Bind bind;
        Class<?> type = method.getDeclaringClass();
        if ((bind = type.getAnnotation(Bind.class)) == null) {
            throw new IllegalStateException("Missing @Bind: " + type.getName());
        }

        if (ms.getSqlCommandType() == SqlCommandType.INSERT) {
            //开启id回写
            Arrays.stream(bind.value().getDeclaredFields())
                    .filter(field -> field.getAnnotation(Id.class) != null)
                    .findFirst()
                    .ifPresent(field -> {
                        Util.setKeyGenerator(ms, Jdbc3KeyGenerator.INSTANCE);
                        Util.setKeyProperty(ms, field.getName(), Util.toColumnName(field));
                    });
        }

        //处理字段映射
        doColumnMapping(bind, method, ms);

        return new DefaultParameterHandler(ms, parameterObject, boundSql);
    }

    static void doColumnMapping(Bind bind, Method method, MappedStatement statement) {
        Map<Field, ColumnMapping> columnMappingMap = Util.toColumnMappingMap(bind);
        if (!columnMappingMap.isEmpty()) {
            List<ResultMap> resultMaps = statement.getResultMaps();
            if (!(resultMaps instanceof ArrayList)) {
                Util.setResultMaps(statement, resultMaps = new ArrayList<>(resultMaps));
            }

            Configuration configuration = statement.getConfiguration();
            List<ResultMapping> resultMappings = columnMappingMap.entrySet().stream()
                    .map(entry -> {
                        Field field = entry.getKey();
                        ColumnMapping columnMapping = entry.getValue();
                        return new ResultMapping.Builder(configuration, field.getName())
                                .column(columnMapping.value())
                                .javaType(field.getType())
                                .build();
                    })
                    .collect(Collectors.toList());

            resultMaps.add(0, new ResultMap.Builder(configuration, "dynamicResultMap", method.getReturnType(), resultMappings).build());
        }
    }

    @Override
    public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType) {
        return new XMLScriptBuilder(configuration, script, parameterType).parseScriptNode();
    }

    @Override
    public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {
        script = PropertyParser.parse(script, configuration.getVariables());
        TextSqlNode textSqlNode = new TextSqlNode(script);
        return textSqlNode.isDynamic() ? new DynamicSqlSource(configuration, textSqlNode) : new RawSqlSource(configuration, script, parameterType);
    }
}
