package example;

import org.ibatis.extension.annotations.Id;

public class User {

    @Id
    public int id;

    public String account;
    public String password;
}
