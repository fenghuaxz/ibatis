package org.ibatis.extension;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

final class TagParser {

    private final Driver driver;

    TagParser(Driver driver) {
        this.driver = driver;
    }

    Annotation replaceTags(Method method, Annotation annotation) {
        if (!(annotation instanceof Insert) && !(annotation instanceof Delete) && !(annotation instanceof Update) && !(annotation instanceof Select)) {
            return annotation;
        }

        String sql = "";
        String databaseId = "";

        if (annotation instanceof Insert) {
            String[] strings = ((Insert) annotation).value();
            databaseId = ((Insert) annotation).databaseId();
            if (strings.length != 1) {
                return annotation;
            }
            sql = SQL.OMIT.equals(strings[0]) ? doOmitFromInsert(method) : strings[0];
        }

        if (annotation instanceof Delete) {
            String[] strings = ((Delete) annotation).value();
            databaseId = ((Delete) annotation).databaseId();
            if (strings.length != 1) {
                return annotation;
            }
            sql = strings[0];
        }

        if (annotation instanceof Update) {
            String[] strings = ((Update) annotation).value();
            databaseId = ((Update) annotation).databaseId();
            if (strings.length != 1) {
                return annotation;
            }
            sql = SQL.OMIT.equals(strings[0]) ? doOmitFromUpdate(method) : strings[0];
        }

        if (annotation instanceof Select) {
            String[] strings = ((Select) annotation).value();
            databaseId = ((Select) annotation).databaseId();
            if (strings.length != 1) {
                return annotation;
            }
            sql = strings[0];
        }

        Bind bind;
        Class<?> type = method.getDeclaringClass();
        if ((bind = type.getAnnotation(Bind.class)) == null) {
            throw new IllegalStateException("Missing @Bind: " + type.getName());
        }

        //解析 @columns标签
        sql = doColumns(bind, sql);

        //解析 @between 和 @notbetween标签
        sql = doBetween(method, sql);
        //where

        //解析 @within标签
        sql = doWithin(bind, method, sql);

        //解析 @without标签
        sql = doWithout(bind, method, sql);

        //为所有column加上转义符
        sql = sql.replaceAll("(?<!`)(?<=\\s)(?!`)(\\w+)(?<!`)(?=\\s*=\\s*#\\{[^}]*\\})", "`$1`");

        String tableName = Util.toTableName(method.getDeclaringClass());

        //替换@table标签为真正的表名
        sql = sql.replace("@table", Util.escape(tableName));

        return new AnnotationBuilder<>(annotation.annotationType())
                .putMember("value", new String[]{sql})
                .putMember("databaseId", databaseId)
                .build();
    }

    private String doOmitFromInsert(Method method) {
        Bind bind;
        Class<?> type = method.getDeclaringClass();
        if ((bind = type.getAnnotation(Bind.class)) == null) {
            throw new IllegalStateException("Missing @Bind: " + type.getName());
        }

        List<String> params = new ArrayList<>();
        Arrays.stream(bind.value().getDeclaredFields()).forEach(field -> {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) ||
                    field.getAnnotation(Id.class) != null) {
                return;
            }
            params.add("#{" + field.getName() + "}");
        });

        String temp = String.valueOf(params);
        temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
        return String.format("INSERT INTO @table(%s) VALUES(%s)", temp.replaceAll("#\\{(.+?)\\}", "`$1`"), temp);
    }

    private String doOmitFromUpdate(Method method) {
        Bind bind;
        Class<?> type = method.getDeclaringClass();
        if ((bind = type.getAnnotation(Bind.class)) == null) {
            throw new IllegalStateException("Missing @Bind: " + type.getName());
        }

        final String[] pk = {null};
        List<String> params = new ArrayList<>();

        Arrays.stream(bind.value().getDeclaredFields()).forEach(field -> {
            String columnName = field.getName();
            if (field.getAnnotation(Id.class) != null) {
                pk[0] = Util.escape(columnName) + "=#{" + columnName + "}";
                return;
            }
            params.add(Util.escape(columnName) + "=#{" + field.getName() + "}");
        });

        if (pk[0] == null) {
            throw new IllegalStateException("Missing @Id: " + bind.value().getName());
        }

        String temp = String.valueOf(params);
        temp = temp.substring(1, temp.length() - 1).replaceAll("\\s+", "");
        return String.format("UPDATE @table SET %s WHERE %s", temp, pk[0]);
    }

    private String doColumns(Bind bind, String sql) {
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

    private String doBetween(Method method, String sql) {
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
