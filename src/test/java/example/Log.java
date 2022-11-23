package example;

import org.ibatis.extension.annotations.ColumnMapping;
import org.ibatis.extension.annotations.Id;

import java.sql.Timestamp;

public class Log {

    @Id
    public int id;
    @ColumnMapping("content2")
    public String content;
    public Timestamp timestamp;

    @Override
    public String toString() {
        return "Log{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
