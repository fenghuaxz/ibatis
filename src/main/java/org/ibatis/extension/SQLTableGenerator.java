package org.ibatis.extension;

import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;
import org.ibatis.extension.annotations.Id;
import org.ibatis.extension.annotations.NotNull;
import org.ibatis.extension.annotations.TypeMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@SuppressWarnings("DuplicatedCode")
final class SQLTableGenerator implements TableGenerator {
    @Override
    public void generate(Bind bind, Class<?> mapper, SqlSession session) {
        String tableName = Util.toTableName(mapper);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ");
        sql.append(Util.escape(tableName));
        sql.append("(");

        String temp = "";
        Id id = null;
        Field idField = null;
        for (Field field : bind.value().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            String jdbcType;
            TypeMapping mapping;
            if ((mapping = field.getAnnotation(TypeMapping.class)) != null) {
                jdbcType = mapping.value();
            } else {
                jdbcType = TypeMapping.Constant.getMapping(field.getType());
            }


            String columnName = Util.toColumnName(field);

            if (id == null && (id = field.getAnnotation(Id.class)) != null) {
                idField = field;
                temp = Util.escape(columnName) + " " + jdbcType + " AUTO_INCREMENT," + temp;
                continue;
            }

            boolean isNotNull = field.getAnnotation(NotNull.class) != null;
            temp += Util.escape(columnName) + " " + jdbcType + (isNotNull ? " NOT NULL," : " NULL,");
        }
        sql.append(temp);

        if (id != null) {
            String columnName = Util.toColumnName(idField);

            sql.append("CONSTRAINT ");
            sql.append(tableName).append("_pk");
            sql.append(" PRIMARY KEY ");
            sql.append("(").append(Util.escape(columnName)).append(")");
        } else {
            sql.deleteCharAt(sql.length() - 1);
        }

        sql.append(") ENGINE=INNODB");

        if (id != null && id.value() > 0) {
            sql.append(" AUTO_INCREMENT=");
            sql.append(id.value());
        }

        sql.append(" DEFAULT CHARSET=utf8;");

        Connection conn = session.getConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.execute();
        } catch (SQLException e) {
            throw new Error("Failed to create table: " + tableName, e);
        }
    }
}
