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
final class SQLiteTableGenerator implements TableGenerator {

    @Override
    @SuppressWarnings("SqlDialectInspection")
    public void generate(Bind bind, Class<?> mapper, SqlSession session) {
        String tableName = Util.toTableName(mapper);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ");
        sql.append(Util.escape(tableName));
        sql.append("(");

        String temp = "";
        Id id = null;
        String pk = null;
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
                pk = columnName;
                temp = Util.escape(columnName) + " INTEGER PRIMARY KEY AUTOINCREMENT," + temp;
                continue;
            }
            boolean isNotNull = field.getAnnotation(NotNull.class) != null;
            temp += Util.escape(columnName) + " " + jdbcType + (isNotNull ? " NOT NULL," : " NULL,");
        }

        sql.append(temp, 0, temp.length() - 1).append(");");

        Connection conn = session.getConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.execute();
            ps.close();

            //设置主键起始位置
            if (pk != null && id.value() > 0) {
                ps = conn.prepareStatement("INSERT INTO sqlite_sequence(name, seq) SELECT ?, ?  WHERE NOT EXISTS(SELECT name FROM sqlite_sequence WHERE name = ?);");
                ps.setString(1, tableName);
                ps.setInt(2, id.value() - 1);
                ps.setString(3, tableName);
                ps.execute();
                ps.close();
                conn.commit();
            }
        } catch (SQLException e) {
            throw new Error("Failed to create table.", e);
        }
    }
}
