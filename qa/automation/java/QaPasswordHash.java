import java.io.*;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class QaPasswordHash {public static void main(String[] args)throws Exception{System.out.print(new BCryptPasswordEncoder().encode(new BufferedReader(new InputStreamReader(System.in)).readLine()));}}
