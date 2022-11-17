package example;

import org.ibatis.extension.Jdbc;

public class AppMain {

    public static void main(String[] args) {

        Jdbc jdbc = new Jdbc.Builder()
                .url("mysql://localhost:3306/test")
                .auth("root", "root")
                .build();


        UserMapper um = jdbc.getMapper(UserMapper.class);

        //添加用户例子
        User user = new User();
        user.account = "fenghuaxz";
        user.password = "112233";
        um.add(user);

        System.out.println("回写id:" + user.id);

        //按账号查询用户例子
        user = um.get("fenghuaxz");
        System.out.println(user);


    }
}
