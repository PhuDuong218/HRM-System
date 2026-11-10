package com.hrm.service;
import java.nio.file.Path;import java.util.Base64;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import org.springframework.web.server.ResponseStatusException;
class LeaveEvidenceStoreTest {
 @TempDir Path directory;
 @Test void uploadedFileIsOwnedAndCanBeRead(){var files=new LeaveEvidenceStore(directory.toString());String data=Base64.getEncoder().encodeToString("%PDF-1.4\nproof".getBytes());var saved=files.upload(7,data);assertDoesNotThrow(()->files.requireOwned(7,saved.reference()));assertThrows(ResponseStatusException.class,()->files.requireOwned(8,saved.reference()));assertEquals(data,files.read(saved.reference()).content());}
 @Test void missingOversizeAndUnsafeFilesRejected(){var files=new LeaveEvidenceStore(directory.toString());assertThrows(ResponseStatusException.class,()->files.requireOwned(7,null));assertThrows(ResponseStatusException.class,()->files.read("../../secret"));assertThrows(ResponseStatusException.class,()->files.upload(7,Base64.getEncoder().encodeToString("<html>".getBytes())));assertThrows(ResponseStatusException.class,()->files.upload(7,"x".repeat(4200001)));}
}
