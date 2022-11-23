package org.ibatis.extension;

import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;

enum Driver {

    MYSQL("com.mysql.cj.jdbc.Driver", new SQLTableGenerator()),
    SQLITE("org.sqlite.JDBC", new SQLiteTableGenerator());

    final String driverName;
    private final TableGenerator generator;

    Driver(String driverName, TableGenerator generator) {
        this.driverName = driverName;
        this.generator = generator;
    }

    void generateTable(Bind bind, Class<?> mapper, SqlSession session) {
        if (generator != null) {
            generator.generate(bind, mapper, session);
        }
    }
}
