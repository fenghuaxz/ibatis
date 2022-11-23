package example;

import org.ibatis.extension.Jdbc;
import org.ibatis.extension.JdbcBuilder;

public class AppMain {

    public static void main(String[] args) {

        Jdbc jdbc = new JdbcBuilder()
                .url("mysql://localhost:3306/test")
                .auth("root", "root")
                .build();



//        TestMapper testMapper = jdbc.getMapper(TestMapper.class);
//        Test test = new Test();
//        test.key ="1";
//        test.value="2";
//        testMapper.add(test);
//
//        System.out.println(testMapper.get("1"));

        LogMapper logMapper = jdbc.getMapper(LogMapper.class);

//        for (int i = 0; i < 5; i++) {
//            Log log = new Log();
//            log.timestamp = new Timestamp(System.currentTimeMillis() - (60 * 60 * 24 * 366*2 * 1000L));
//            log.content = "时间:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(log.timestamp.getTime()));
//            logMapper.add(log);
//        }

        Log[] logs = logMapper.get(0);
        System.out.println("结果:" + logs.length);

        for (Log log : logs) {
            System.out.println("内容:" + log);
        }

    }
}
