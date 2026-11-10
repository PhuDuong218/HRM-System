package com.hrm.service;
import java.nio.file.*;import java.io.*;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;
@Service public class LeaveEvidenceStore {
 private final Path root;
 public LeaveEvidenceStore(@Value("${hrm.leave.evidence-directory:${user.home}/.hrm/leave-evidence}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
 public record Upload(String content){}
 public record FileData(String reference,String content,String contentType){}
 public FileData upload(long owner,String encoded){
  if(encoded==null||encoded.length()>4200000)throw PersonnelWorkflowSupport.bad("Minh chứng tối đa 3 MB");
  byte[] data;try{data=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw PersonnelWorkflowSupport.bad("Tệp minh chứng không hợp lệ");}
  if(data.length==0||data.length>3*1024*1024)throw PersonnelWorkflowSupport.bad("Minh chứng tối đa 3 MB");
  String ext=extension(data);String ref=owner+"/"+UUID.randomUUID()+ext;
  try{Path file=root.resolve(ref);Files.createDirectories(file.getParent());Files.write(file,data,StandardOpenOption.CREATE_NEW);}catch(IOException e){throw new IllegalStateException("Không lưu được minh chứng",e);}
  return new FileData(ref,null,type(ext));
 }
 private String extension(byte[] d){if(d.length>4&&d[0]=='%'&&d[1]=='P'&&d[2]=='D'&&d[3]=='F')return ".pdf";if(d.length>3&&(d[0]&255)==255&&(d[1]&255)==216&&(d[2]&255)==255)return ".jpg";if(d.length>=8&&java.util.Arrays.equals(java.util.Arrays.copyOf(d,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return ".png";throw PersonnelWorkflowSupport.bad("Chỉ chấp nhận ảnh JPG, PNG hoặc PDF");}
 private Path path(String ref){if(ref==null||!ref.matches("[1-9][0-9]*/[a-f0-9-]{36}\\.(pdf|jpg|png)"))throw PersonnelWorkflowSupport.bad("Minh chứng không hợp lệ");return root.resolve(ref).normalize();}
 public void requireOwned(long owner,String ref){if(ref==null||!ref.startsWith(owner+"/")||!Files.isRegularFile(path(ref)))throw PersonnelWorkflowSupport.bad("Cần đính kèm minh chứng hợp lệ của bạn");}
 public FileData read(String ref){try{byte[] d=Files.readAllBytes(path(ref));return new FileData(ref,Base64.getEncoder().encodeToString(d),type(extension(d)));}catch(IOException e){throw PersonnelWorkflowSupport.conflict("Không tìm thấy tệp minh chứng");}}
 private String type(String ext){return switch(ext){case ".pdf"->"application/pdf";case ".png"->"image/png";default->"image/jpeg";};}
}
