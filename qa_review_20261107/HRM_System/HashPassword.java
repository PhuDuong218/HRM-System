import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashPassword {
    public static void main(String[] args) {
        System.out.println(
            new BCryptPasswordEncoder().encode("Admin@123")
        );
    }
}