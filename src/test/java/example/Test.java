package example;

import org.ibatis.extension.annotations.ColumnMapping;
import org.ibatis.extension.annotations.Id;

public class Test {

    @Id
//    @ColumnMapping("table2")
    public int table;
    public String key;
    public String value;
}
