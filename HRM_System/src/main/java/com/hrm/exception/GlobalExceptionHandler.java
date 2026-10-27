package com.hrm.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrentChange(ConcurrencyFailureException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Dữ liệu vừa thay đổi hoặc đang được xử lý; hãy tải lại và thử lại");
    }

    @ExceptionHandler(UncategorizedSQLException.class)
    public ResponseEntity<Object> handleAssignmentConstraint(UncategorizedSQLException ex) {
        int code = ex.getSQLException().getErrorCode();
        if (code >= 51100 && code <= 51199) {
            return buildErrorResponse(HttpStatus.CONFLICT,
                    "Phân công/trưởng phòng không hợp lệ hoặc chồng hiệu lực; hãy kiểm tra dữ liệu và thử lại");
        }
        log.error("Loi SQL chua duoc xu ly", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Máy chủ không xử lý được yêu cầu");
    }

    // 1. Sai phương thức HTTP
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Phương thức HTTP không được hỗ trợ"
        );
    }

    // 2. Validation DTO thất bại
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ");

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // 3. Không tìm thấy dữ liệu
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(
            ResourceNotFoundException ex) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
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

    // 4. JSON sai định dạng
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParseException(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng (có thể dư dấu phẩy hoặc sai ngoặc).");
    }

    // 5. Content-Type không được hỗ trợ
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {

        return buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type không được hỗ trợ. Hãy sử dụng application/json"
        );
    }

    // 6. Vi phạm ràng buộc database
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Dữ liệu trùng hoặc vi phạm ràng buộc database"
        );
    }

    // 7. API không tồn tại
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(
            NoResourceFoundException ex) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Đường dẫn API không tồn tại"
        );
    }

    // 8. Lỗi nghiệp vụ
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(
            BusinessException ex) {

        return buildErrorResponse(
                ex.getStatus(),
                ex.getMessage()
        );
    }

    // 9. Sai kiểu tham số
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Tham số " + ex.getName() + " không đúng định dạng"
        );
    }

    // 10. Giữ nguyên HTTP status của ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(
            ResponseStatusException ex) {

        String message = ex.getReason() != null
                ? ex.getReason()
                : "Yêu cầu không hợp lệ";

        return buildErrorResponse(
                ex.getStatusCode(),
                message
        );
    }

    // 11. Những lỗi RuntimeException chưa được xử lý
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex) {

        // In lỗi thật ra Terminal để lập trình viên kiểm tra
        log.error("Loi Backend chua duoc xu ly", ex);

        // Không trả chi tiết lỗi nội bộ cho người dùng
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Máy chủ không xử lý được yêu cầu"
        );
    }

    // 12. Định dạng lỗi thống nhất
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
