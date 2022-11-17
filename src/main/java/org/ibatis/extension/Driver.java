package org.ibatis.extension;

import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;

enum Driver {

    MYSQL("com.mysql.cj.jdbc.Driver", new SQLTableGenerator()),
    SQLITE("org.sqlite.JDBC", new SQLiteTableGenerator());

    final String driver;
    private final TableGenerator generator;

    Driver(String driver, TableGenerator generator) {
        this.driver = driver;
        this.generator = generator;
    }

    void generateTable(Bind bind, Class<?> mapper, SqlSession session) {
        if (generator != null) {
            generator.generate(this, bind, mapper, session);
        }
    }

    String tableName(Class<?> clazz) {
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
}
