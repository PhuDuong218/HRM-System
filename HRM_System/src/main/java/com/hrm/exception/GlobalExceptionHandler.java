
package com.hrm.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Xử lý lỗi 405: Sai phương thức HTTP
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Lỗi thao tác: Bạn đang dùng sai phương thức "
                        + "(Ví dụ: Cần PUT nhưng lại dùng POST).");
    }

    // 2. Xử lý lỗi 400: Dữ liệu không hợp lệ do @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ");

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                errorMessage);
    }

    // 3. Xử lý lỗi 404: Không tìm thấy tài nguyên
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(
            ResourceNotFoundException ex) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage());
    }

    // 4. Xử lý lỗi nghiệp vụ
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(
            BusinessException ex) {

        return buildErrorResponse(
                ex.getStatus(),
                ex.getMessage());
    }

    // 5. Xử lý lỗi 401: Xác thực không thành công
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(
            AuthenticationException ex) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Tên đăng nhập hoặc mật khẩu không đúng.");
    }

    // 6. Xử lý lỗi 403: Không có quyền truy cập
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(
            AccessDeniedException ex) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.");
    }

    // 7. Xử lý lỗi 400: Tham số nghiệp vụ không hợp lệ
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage());
    }

    // 8. Xử lý ResponseStatusException
    // Giữ nguyên HTTP status do Service chỉ định
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(
            ResponseStatusException ex) {

        return buildErrorResponse(
                ex.getStatusCode(),
                ex.getReason() != null
                        ? ex.getReason()
                        : "Yêu cầu không thể thực hiện");
    }

    // 9. Xử lý lỗi 400: JSON sai định dạng
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParseException(
            HttpMessageNotReadableException ex) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng "
                        + "(có thể dư dấu phẩy hoặc sai ngoặc).");
    }

    // 10. Xử lý lỗi 409: Vi phạm ràng buộc dữ liệu
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Lỗi xung đột dữ liệu: Mã bạn nhập đã tồn tại "
                        + "trong hệ thống hoặc vi phạm ràng buộc.");
    }

    // 11. Xử lý lỗi 404: Đường dẫn API không tồn tại
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(
            NoResourceFoundException ex) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Lỗi 404: Đường dẫn API không tồn tại, "
                        + "vui lòng kiểm tra lại URL.");
    }

    // 12. Xử lý lỗi 400: Tham số sai kiểu dữ liệu
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Tham số " + ex.getName() + " không đúng định dạng");
    }

    // 13. Xử lý các RuntimeException chưa được xử lý riêng
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex) {

        // Không trả thông tin lỗi nội bộ về client
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Máy chủ không xử lý được yêu cầu");
    }

    // 14. Hàm chung tạo ResponseEntity dạng JSON
    private ResponseEntity<Object> buildErrorResponse(
            HttpStatusCode status,
            String message) {

        Map<String, Object> error = new HashMap<>();

        error.put("thoi_gian", LocalDateTime.now());
        error.put("trang_thai", status.value());
        error.put("loi_chi_tiet", message);

        return new ResponseEntity<>(error, status);
    }
}
