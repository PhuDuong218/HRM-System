package com.hrm.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice // Đánh dấu đây là Trạm kiểm soát lỗi toàn cầu
public class GlobalExceptionHandler {

    // 1. Tóm cổ lỗi 405 (Sai phương thức GET/POST/PUT/DELETE)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Lỗi thao tác: Bạn đang dùng sai phương thức (Ví dụ: Cần PUT nhưng lại dùng POST).");
    }

    // 2. Tóm cổ lỗi 400 (Do Lính gác @Valid bắt được: Để trống tên, sai định dạng)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Lấy câu thông báo lỗi đầu tiên trong file DTO (Ví dụ: "Tên phòng ban không được để trống")
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // 3. Tóm cổ các lỗi 500 tự định nghĩa (Ví dụ: "Không tìm thấy phòng ban")
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

@ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParseException(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng (có thể dư dấu phẩy hoặc sai ngoặc).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Lỗi xung đột dữ liệu: Mã bạn nhập đã tồn tại trong hệ thống hoặc vi phạm ràng buộc.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(NoResourceFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Lỗi 404: Đường dẫn API không tồn tại, vui lòng kiểm tra lại URL.");
    }

    // --- Hàm Helper để đóng gói JSON báo lỗi cho đẹp ---
    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("thoi_gian", LocalDateTime.now());
        error.put("trang_thai", status.value());
        error.put("loi_chi_tiet", message);
        return new ResponseEntity<>(error, status);
    }
}