package com.hrm.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice // Đánh dấu đây là Trạm kiểm soát lỗi toàn cầu
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "Lỗi thao tác: Bạn đang dùng sai phương thức (Ví dụ: Cần PUT nhưng lại dùng POST).");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Lấy câu thông báo lỗi đầu tiên trong file DTO (Ví dụ: "Tên phòng ban không
        // được để trống")
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParseException(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng (có thể dư dấu phẩy hoặc sai ngoặc).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT,
                "Lỗi xung đột dữ liệu: Mã bạn nhập đã tồn tại trong hệ thống hoặc vi phạm ràng buộc.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(NoResourceFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND,
                "Lỗi 404: Đường dẫn API không tồn tại, vui lòng kiểm tra lại URL.");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        // Tranh tra noi dung loi noi bo ve client.
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "May chu khong xu ly duoc yeu cau");
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatusCode status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("thoi_gian", LocalDateTime.now());
        error.put("trang_thai", status.value());
        error.put("loi_chi_tiet", message);
        return new ResponseEntity<>(error, status);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex) {
        return buildErrorResponse(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Tham số " + ex.getName() + " không đúng định dạng");
    }
}
