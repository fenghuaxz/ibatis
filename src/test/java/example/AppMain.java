package example;


import org.ibatis.extension.SQL;

import java.util.UUID;

public class AppMain {

    public static void main(String[] args) {

        SQL sql = new SQL.Builder()
                .url("mysql://localhost:3306/script")
                .auth("root", "Loveyi8023")
                .build();


//        TestMapper testMapper = jdbc.getMapper(TestMapper.class);
//        Test test = new Test();
//        test.key ="1";
//        test.value="2";
//        testMapper.add(test);
//
//        System.out.println(testMapper.get("1"));

        LogMapper logMapper = sql.getMapper(LogMapper.class);

//        for (int i = 0; i < 5; i++) {
//            Log log = new Log();
//            log.timestamp = new Timestamp(System.currentTimeMillis() - (60 * 60 * 24 * 366*2 * 1000L));
//            log.content = "时间:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(log.timestamp.getTime()));
//            logMapper.add(log);
//        }

        UserMapper mapper = sql.getMapper(UserMapper.class);

        User user = mapper.findByAccount("fenghuaxz");
        user.sid = UUID.randomUUID().toString();

        mapper.update(user);
        System.out.println(user);
    }
}
