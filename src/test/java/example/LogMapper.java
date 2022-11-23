package example;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.ibatis.extension.annotations.Bind;

@Bind(Log.class)
public interface LogMapper {

    @Insert("@omit")
    void add(Log log);

    @Update("@omit")
    void update(Log log);

    @Select("select @columns from @table where @without(timestamp,day,7)")
    Log[] get(@Param("time")int time);
}
