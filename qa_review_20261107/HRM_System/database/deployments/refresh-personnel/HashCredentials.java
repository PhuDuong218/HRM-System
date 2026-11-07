import java.io.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashCredentials {
 public static void main(String[] args)throws Exception{
  var encoder=new BCryptPasswordEncoder(10);var reader=new BufferedReader(new InputStreamReader(System.in));String line;
  while((line=reader.readLine())!=null){var parts=line.split("\t",2);System.out.println(parts[0]+"\t"+encoder.encode(parts[1]));}
 }
}
