package example;

import org.ibatis.extension.annotations.Id;

public class User {

    @Id(1000)
    public int id;
    public String sid;
    public String account;
    public String password;
    public String email = "";
    public int money;

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", sid='" + sid + '\'' +
                ", account='" + account + '\'' +
                ", password='" + password + '\'' +
                ", email='" + email + '\'' +
                ", money=" + money +
                '}';
    }
}
