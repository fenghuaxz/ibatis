package org.ibatis.extension;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.UnknownTypeHandler;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.DateFormat;
import org.ibatis.extension.annotations.Id;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("ALL")
final class DefaultMapperRegistry extends MapperRegistry {

    private final Driver driver;

    public DefaultMapperRegistry(Configuration config, Driver driver) {
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

    private void replaceTags(Bind bind, Class<?> clazz) {
        String tableName = Utils.toTableName(clazz);
        Map<String, String> columnMappingMap = Utils.toColumnMappingMap(bind);

        for (Method method : clazz.getMethods()) {
            Annotation a;
            if ((a = method.getAnnotation(Insert.class)) != null || (a = method.getAnnotation(Delete.class)) != null ||
                    (a = method.getAnnotation(Update.class)) != null || (a = method.getAnnotation(Select.class)) != null) {

                Map<String, Object> memberValues = Utils.getMemberValuesMap(a);
                String sql = ((String[]) memberValues.get("value"))[0];

                if (a instanceof Insert)
                    sql = doInsert(bind, method, sql);
                else if (a instanceof Delete)
                    sql = doDelete(bind, method, sql);
                else if (a instanceof Update)
                    sql = doUpdate(bind, method, sql);
                else {
                    sql = doSelect(bind, method, sql, columnMappingMap);
                }

                //解析 @columns标签
                sql = doColumns(bind, method, sql);

                //解析 @between 和 @notbetween标签
                sql = doBetween(bind, method, sql);

                //解析 @within标签
                sql = doWithin(bind, method, sql);

                //解析 @without标签
                sql = doWithout(bind, method, sql);

                //为所有column加上转义符
                for (Field field : bind.value().getDeclaredFields()) {
                    sql = sql.replaceAll("(?<!\\@|`|\\{)" + field.getName() + "(?!\\})", "`$0`");
                }

                //处理字段映射
                if (columnMappingMap.size() != 0) {
                    for (Map.Entry<String, String> entry : columnMappingMap.entrySet()) {
                        sql = sql.replaceAll("(?<!\\@|\\{)" + entry.getKey() + "(?!\\})", entry.getValue());
                    }
                }

                //替换@table标签为真正的表名
                memberValues.put("value", new String[]{sql.replace("@table", Utils.escape(tableName))});
            }
        }
    }

    private String doInsert(Bind bind, Method method, String sql) {
        if (!Jdbc.OMIT.equals(sql)) {
            return sql;
        }

        Field[] fields = bind.value().getDeclaredFields();
        List<String> params = new ArrayList<>();
        Field idField = null;
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())) {
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
        sql = String.format("INSERT INTO @table(%s) VALUES(%s)", temp.replaceAll("#\\{|}", "`"), temp);

        if (idField != null) {
            //开启回写id
            Map<String, Object> valuesMap = new HashMap<>();
            valuesMap.put("useCache", true);
            valuesMap.put("flushCache", Options.FlushCachePolicy.DEFAULT);
            valuesMap.put("resultSetType", ResultSetType.DEFAULT);
            valuesMap.put("statementType", StatementType.PREPARED);
            valuesMap.put("fetchSize", -1);
            valuesMap.put("timeout", -1);
            valuesMap.put("resultSets", "");
            valuesMap.put("useGeneratedKeys", true);
            valuesMap.put("keyProperty", idField.getName());
            valuesMap.put("keyColumn", idField.getName());
            valuesMap.put("databaseId", "");
            Utils.add(method, Options.class, Utils.create(Options.class, valuesMap));
        }
        return sql;
    }

    private String doDelete(Bind bind, Method method, String sql) {
        return sql;
    }

    private String doUpdate(Bind bind, Method method, String sql) {
        if (!Jdbc.OMIT.equals(sql)) {
            return sql;
        }
        String pk = null;
        List<String> params = new ArrayList<>();
        for (Field field : bind.value().getDeclaredFields()) {
            String columnName = field.getName();
            if (field.getAnnotation(Id.class) != null) {
                pk = Utils.escape(columnName) + "=#{" + columnName + "}";
                continue;
            }
            params.add(Utils.escape(columnName) + "=#{" + field.getName() + "}");
        }

        if (pk == null) {
            throw new IllegalStateException("Missing @Id: " + bind.value().getName());
        }

        String temp = String.valueOf(params);
        temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
        sql = String.format("UPDATE @table SET %s WHERE %s", temp, pk);
        return sql;
    }

    private String doSelect(Bind bind, Method method, String sql, Map<String, String> columnMappingMap) {

        //查询结果字段映射
        if (columnMappingMap.size() != 0) {
            List<Result> results = new ArrayList<>();
            for (Map.Entry<String, String> entry : columnMappingMap.entrySet()) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("id", false);
                resultMap.put("javaType", void.class);
                resultMap.put("jdbcType", JdbcType.UNDEFINED);
                resultMap.put("typeHandler", UnknownTypeHandler.class);
                resultMap.put("one", Utils.one());
                resultMap.put("many", Utils.many());
                resultMap.put("property", entry.getKey());
                resultMap.put("column", entry.getValue());
                results.add(Utils.create(Result.class, resultMap));
            }

            Map<String, Object> resultsMap = new HashMap<>();
            resultsMap.put("id", "");
            resultsMap.put("value", results.toArray(new Result[0]));
            Utils.add(method, Results.class, Utils.create(Results.class, resultsMap));
        }

        //如果返回值数量不是数组 那就补上限制只需要一个查询结果 避免踩坑
        if (!method.getReturnType().isArray() && !sql.matches(".*?limit\\s+1")) {
            sql = sql.concat(" limit 1");
        }
        return sql;
    }

    private String doColumns(Bind bind, Method method, String sql) {
        Matcher matcher = Pattern.compile("@columns\\(.*?\\)|@columns").matcher(sql);
        if (matcher.find()) {
            final String tag = matcher.group();

            Set<String> exclude = new HashSet<>();
            String temp = tag.replaceAll("@columns|\\(|\\)|\\s+", "");
            if (!temp.isEmpty()) {
                exclude.addAll(Arrays.asList(temp.split(",")));
            }

            List<String> columnsName = new ArrayList<>();
            for (Field field : bind.value().getDeclaredFields()) {
                String fieldName = field.getName();
                if (!exclude.contains(fieldName)) {
                    columnsName.add(fieldName);
                }
            }

            temp = String.valueOf(columnsName);
            temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
            sql = sql.replace(tag, temp);

        }
        return sql;
    }

    private String doBetween(Bind bind, Method method, String sql) {
        Matcher matcher = Pattern.compile("(@between\\(.*?\\)|@notbetween\\(.*?\\))").matcher(sql);
        if (matcher.find()) {
            final String tag = matcher.group().toLowerCase();

            String items = tag.replaceAll("(@between|@notbetween)|\\(|\\)|\\s+", "");
            String[] args = items.split(",");

            if (args.length != 3) return sql;

            String column = args[0], start = args[1], end = args[2];

            //日期样式
            String style = "'%Y-%m-%d'";
            DateFormat dateFormat;
            if ((dateFormat = method.getAnnotation(DateFormat.class)) != null) {
                style = dateFormat.value();
            }

            //为日期样式加上单引号
            style = style.replaceAll("(?!\\')" + style, "'$0'");

            String temp = "";

            switch (driver) {
                case MYSQL:
                    temp = temp.concat("FROM_UNIXTIME(UNIX_TIMESTAMP(" + column + ")," + style + ")");
                    temp = temp.concat(tag.contains("@between") ? " BETWEEN " : " NOT BETWEEN ");
                    temp = temp.concat(String.format("CAST(%s as DATE) AND CAST(%s as DATE)", start, end));
                    sql = sql.replace(tag, temp);
                    break;

                case SQLITE:
                    temp = temp.concat("strftime(" + style + ",datetime(" + column + "/1000,'unixepoch','localtime'))");
                    temp = temp.concat(tag.contains("@between") ? " BETWEEN " : " NOT BETWEEN ");
                    temp = temp.concat(String.format("%s AND %s", start, end));
                    sql = sql.replace(tag, temp);
                    break;

                default:
                    break;
            }
        }
        return sql;
    }

    private String doWithin(Bind bind, Method method, String sql) {
        Matcher matcher = Pattern.compile("@within\\(.*?\\)").matcher(sql);
        if (matcher.find()) {
            final String tag = matcher.group();
            String[] args = tag.replaceAll("@within|\\(|\\)|\\s+", "").split(",");

            if (args.length != 3) return sql;

            String column = args[0], unit = args[1], value = args[2];

            String temp = "";
            switch (driver) {
                case MYSQL:
                    switch (unit.toUpperCase()) {
                        case "SECOND":
                        case "MINUTE":
                        case "HOUR":
                            temp = temp.concat("ABS(TIMESTAMPDIFF(" + unit.toUpperCase() + ", NOW(), FROM_UNIXTIME(UNIX_TIMESTAMP(" + column + ")))) <= " + value);
                            break;

                        case "DAY":
                            temp = temp.concat("DATEDIFF(NOW(),FROM_UNIXTIME(UNIX_TIMESTAMP(" + column + "))) <= " + value);
                            break;

                        case "MONTH":
                            temp = temp.concat("MONTH(" + column + ") >= MONTH(DATE_SUB(NOW(),INTERVAL " + value + " MONTH))");
                            break;

                        case "YEAR":
                            temp = temp.concat("YEAR(" + column + ") >= YEAR(DATE_SUB(NOW(),INTERVAL " + value + " YEAR))");
                            break;

                        default:
                            break;
                    }
                    break;

                case SQLITE:
                    switch (unit.toUpperCase()) {
                        case "SECOND":
                        case "MINUTE":
                        case "HOUR":

                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" BETWEEN ");
                            temp = temp.concat("datetime('now','localtime','-'||abs(0-" + value + ")||' " + unit.toLowerCase() + "')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','localtime','+'||abs(" + value + "+1)||' " + unit.toLowerCase() + "')");
                            break;

                        case "DAY":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" BETWEEN ");
                            temp = temp.concat("datetime('now','start of day','-'||abs(0-" + value + ")||' day')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of day','+'||abs(" + value + "+1)||' day')");
                            break;

                        case "MONTH":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" BETWEEN ");
                            temp = temp.concat("datetime('now','start of month','-'||abs(0-" + value + ")||' month')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of month','+'||abs(" + value + "+1)||' month')");
                            break;

                        case "YEAR":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" BETWEEN ");
                            temp = temp.concat("datetime('now','start of year','-'||abs(0-" + value + ")||' year')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of year','+'||abs(" + value + "+1)||' year')");
                            break;
                    }
                    break;

                default:
                    break;
            }
            sql = sql.replace(tag, temp);
        }
        return sql;
    }

    private String doWithout(Bind bind, Method method, String sql) {
        Matcher matcher = Pattern.compile("@without\\(.*?\\)").matcher(sql);
        if (matcher.find()) {
            final String tag = matcher.group();
            String[] args = tag.replaceAll("@without|\\(|\\)|\\s+", "").split(",");

            if (args.length != 3) return sql;

            String column = args[0], unit = args[1], value = args[2];

            String temp = "";
            switch (driver) {
                case MYSQL:

                    switch (unit.toUpperCase()) {
                        case "SECOND":
                        case "MINUTE":
                        case "HOUR":
                            temp = temp.concat("ABS(TIMESTAMPDIFF(" + unit.toUpperCase() + ", NOW(), FROM_UNIXTIME(UNIX_TIMESTAMP(" + column + ")))) > " + value);
                            break;

                        case "DAY":
                            temp = temp.concat("DATEDIFF(NOW(),FROM_UNIXTIME(UNIX_TIMESTAMP(" + column + "))) > " + value);
                            break;

                        case "MONTH":
                            temp = temp.concat("MONTH(" + column + ") < MONTH(DATE_SUB(NOW(),INTERVAL " + value + " MONTH))");
                            break;

                        case "YEAR":
                            temp = temp.concat("YEAR(" + column + ") < YEAR(DATE_SUB(NOW(),INTERVAL " + value + " YEAR))");
                            break;

                        default:
                            break;
                    }
                    break;

                case SQLITE:
                    switch (unit.toUpperCase()) {
                        case "SECOND":
                        case "MINUTE":
                        case "HOUR":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" NOT BETWEEN ");
                            temp = temp.concat("datetime('now','localtime','-'||abs(0-" + value + ")||' " + unit.toLowerCase() + "')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','localtime','+'||abs(" + value + "+1)||' " + unit.toLowerCase() + "')");
                            break;

                        case "DAY":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" NOT BETWEEN ");
                            temp = temp.concat("datetime('now','start of day','-'||abs(0-" + value + ")||' day')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of day','+'||abs(" + value + "+1)||' day')");
                            break;

                        case "MONTH":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" NOT BETWEEN ");
                            temp = temp.concat("datetime('now','start of month','-'||abs(0-" + value + ")||' month')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of month','+'||abs(" + value + "+1)||' month')");
                            break;

                        case "YEAR":
                            temp = temp.concat("datetime(timestamp/1000, 'unixepoch', 'localtime')");
                            temp = temp.concat(" NOT BETWEEN ");
                            temp = temp.concat("datetime('now','start of year','-'||abs(0-" + value + ")||' year')");
                            temp = temp.concat(" AND ");
                            temp = temp.concat("datetime('now','start of year','+'||abs(" + value + "+1)||' year')");
                            break;
                    }
                    break;

                default:
                    break;
            }
            sql = sql.replace(tag, temp);
        }
        return sql;
    }
}
