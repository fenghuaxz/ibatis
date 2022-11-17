package org.ibatis.extension;

import org.apache.ibatis.session.SqlSession;
import org.ibatis.extension.annotations.Bind;

interface TableGenerator {

    void generate(Driver driver, Bind bind, Class<?> mapper, SqlSession session);
}
