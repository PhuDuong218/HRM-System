
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // Compatibility entry point; only handleIllegalArgumentException
        // is registered as a handler.
        public ResponseEntity<Object> invalidArgument(
                        IllegalArgumentException ex) {

                return handleIllegalArgumentException(ex);
        }

        // 1. Xu ly loi dong thoi
        @ExceptionHandler(ConcurrencyFailureException.class)
        public ResponseEntity<Object> handleConcurrentChange(
                        ConcurrencyFailureException ex) {

                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                "Dữ liệu vừa thay đổi hoặc đang được xử lý; hãy tải lại và thử lại");
        }

        // 2. Xu ly rang buoc SQL nghiep vu
        @ExceptionHandler(UncategorizedSQLException.class)
        public ResponseEntity<Object> handleAssignmentConstraint(
                        UncategorizedSQLException ex) {

                int code = ex.getSQLException().getErrorCode();

                // Thong bao rieng cho loi phan cong va truong phong
                if (code >= 51100 && code <= 51199) {
                        return buildErrorResponse(
                                        HttpStatus.CONFLICT,
                                        "Phân công/trưởng phòng không hợp lệ hoặc chồng hiệu lực; hãy kiểm tra dữ liệu và thử lại");
                }

                // Cac loi rang buoc nghiep vu khac tu main
                if (code >= 51000 && code <= 51399) {
                        return buildErrorResponse(
                                        HttpStatus.CONFLICT,
                                        "Dữ liệu nghiệp vụ không hợp lệ, chồng hiệu lực hoặc lịch sử đang được bảo vệ");
                }

                log.error("Loi SQL chua duoc xu ly", ex);

                return buildErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Máy chủ không xử lý được yêu cầu");
        }

        // 3. Sai phuong thuc HTTP
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<Object> handleMethodNotSupported(
                        HttpRequestMethodNotSupportedException ex) {

                return buildErrorResponse(
                                HttpStatus.METHOD_NOT_ALLOWED,
                                "Lỗi thao tác: Bạn đang dùng sai phương thức "
                                                + "(Ví dụ: Cần PUT nhưng lại dùng POST).");
        }

        // 4. Loi validation DTO
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
                                message);
        }

        // 5. Khong tim thay du lieu
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Object> handleResourceNotFound(
                        ResourceNotFoundException ex) {

                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage());
        }

        // 6. Loi nghiep vu
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Object> handleBusinessException(
                        BusinessException ex) {

                return buildErrorResponse(
                                ex.getStatus(),
                                ex.getMessage());
        }

        // 7. Loi xac thuc
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<Object> handleAuthenticationException(
                        AuthenticationException ex) {

                return buildErrorResponse(
                                HttpStatus.UNAUTHORIZED,
                                "Tên đăng nhập hoặc mật khẩu không đúng.");
        }

        // 8. Khong co quyen truy cap
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Object> handleAccessDeniedException(
                        AccessDeniedException ex) {

                return buildErrorResponse(
                                HttpStatus.FORBIDDEN,
                                "Bạn không có quyền thực hiện thao tác này.");
        }

        // 9. Tham so nghiep vu khong hop le
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Object> handleIllegalArgumentException(
                        IllegalArgumentException ex) {

                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage());
        }

        // 10. Giu nguyen HTTP status tu Service
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<Object> handleResponseStatusException(
                        ResponseStatusException ex) {

                return buildErrorResponse(
                                ex.getStatusCode(),
                                ex.getReason() != null
                                                ? ex.getReason()
                                                : "Yêu cầu không thể thực hiện");
        }

        // 11. JSON sai dinh dang
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Object> handleJsonParseException(
                        HttpMessageNotReadableException ex) {

                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng "
                                                + "(có thể dư dấu phẩy hoặc sai ngoặc).");
        }

        // 12. Content-Type khong duoc ho tro
        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<Object> handleUnsupportedMediaType(
                        HttpMediaTypeNotSupportedException ex) {

                return buildErrorResponse(
                                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                "Content-Type không được hỗ trợ. Hãy sử dụng application/json");
        }

        // 13. Vi pham rang buoc du lieu
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Object> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {

                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                "Lỗi xung đột dữ liệu: Mã bạn nhập đã tồn tại "
                                                + "trong hệ thống hoặc vi phạm ràng buộc.");
        }

        // 14. Duong dan API khong ton tai
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<Object> handleNotFoundException(
                        NoResourceFoundException ex) {

                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Lỗi 404: Đường dẫn API không tồn tại, "
                                                + "vui lòng kiểm tra lại URL.");
        }

        // 15. Tham so sai kieu du lieu
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Object> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex) {

                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Tham số " + ex.getName() + " không đúng định dạng");
        }

        // 16. Loi validation tren tham so phuong thuc
        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<Object> handleMethodValidation(
                        HandlerMethodValidationException ex) {

                return buildErrorResponse(
                                ex.getStatusCode(),
                                "Tham số yêu cầu không hợp lệ");
        }

        // 17. Loi RuntimeException chua duoc xu ly
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Object> handleRuntimeException(
                        RuntimeException ex) {

                log.error("Loi Backend chua duoc xu ly", ex);

                return buildErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Máy chủ không xử lý được yêu cầu");
        }

        // 18. Dinh dang JSON loi thong nhat
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
