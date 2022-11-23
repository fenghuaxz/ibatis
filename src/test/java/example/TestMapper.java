package example;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ibatis.extension.annotations.Bind;

@Bind(Test.class)
public interface TestMapper {

    @Insert("@omit")
    void add(Test test);

    @Select("select @columns(value) from @table where table=#{table}")
    Test get(@Param("table") String key);
}
