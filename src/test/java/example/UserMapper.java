package example;

import org.apache.ibatis.annotations.*;
import org.ibatis.extension.Jdbc;
import org.ibatis.extension.annotations.Bind;

@Bind(User.class)
public interface UserMapper {

    @Insert(Jdbc.OMIT)
    void add(User user);

    @Update(Jdbc.OMIT)
    void update(User user);

    @Select("select*from @table where account=#{account} @between(#{start},#{end})")
    User get(@Param("account") String account);
}
