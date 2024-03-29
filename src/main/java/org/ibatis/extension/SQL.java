package org.ibatis.extension;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.TimeZone;

public interface SQL {

    String OMIT = "@omit";

    <T> T getMapper(Class<T> mapper);

    final class Builder {

        private String url;
        private Driver driver;
        private final Properties properties;

        public Builder() {
            this.properties = new Properties();
            this.option("serverTimezone", TimeZone.getDefault().getID());
            this.option("allowPublicKeyRetrieval", "true");
            this.option("useUnicode", "true");
            this.option("characterEncoding", "utf-8");
            this.option("useSSL", "false");
        }

        public Builder url(String url) {
            String tempUrl = url.replace("jdbc:", "");

            if (tempUrl.startsWith("mysql:"))
                driver = Driver.MYSQL;
            else if (tempUrl.startsWith("sqlite:"))
                driver = Driver.SQLITE;
            else  {
                throw new UnsupportedOperationException("Must be prefixed with 'mysql:' or 'sqlite:'");
            }
            this.url = "jdbc:".concat(tempUrl);
            return this;
        }

        public Builder auth(String user, String password) {
            this.option("user", user);
            this.option("password", password);
            return this;
        }

        public Builder option(String key, String value) {
            this.properties.setProperty(key, value);
            return this;
        }

        public SQL build() {
            return build(null);
        }

        public SQL build(Configuration config) {
            if (config == null) {
                config = new Configuration();
            }

            DataSource ds = new PooledDataSource(this.driver.driverName, this.url, this.properties);
            Environment env = new Environment("mybatis", new JdbcTransactionFactory(), ds);
            config.setEnvironment(env);

            try {
                Field field = Configuration.class.getDeclaredField("mapperRegistry");
                field.setAccessible(true);
                field.set(config, new DefaultMapperRegistry(config, this.driver));
                return new DefaultSQL(new SqlSessionFactoryBuilder().build(config));
            } catch (Exception e) {
                throw new Error("Failed to set mapperRegistry.", e);
            }
        }
    }
}