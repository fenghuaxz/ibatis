package example;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.ibatis.extension.annotations.Bind;

@Bind(User.class)
public interface UserMapper {

    @Insert("@omit")
    void add(User user);

    @Update("@omit")
    void update(User user);

    @Select("select*from @table where account=#{account}")
    User findByAccount(@Param("account") String account);

    @Select("select*from @table where id=#{uid}")
    User findByUid(@Param("uid") int uid);

    @Select("select*from @table where sid=#{sid}")
    User findBySid(@Param("sid") String sid);

    @Select("select sum(money) from @table")
    int getTotalMoney();
}
