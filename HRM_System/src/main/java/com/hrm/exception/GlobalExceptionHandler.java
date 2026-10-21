
package com.hrm.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Xử lý lỗi 405: Sai phương thức GET/POST/PUT/DELETE
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Lỗi thao tác: Bạn đang dùng sai phương thức "
                        + "(Ví dụ: Cần PUT nhưng lại dùng POST)."
        );
    }

    // 2. Xử lý lỗi 400: Dữ liệu không hợp lệ do @Valid phát hiện
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                errorMessage
        );
    }

    // 3. Xử lý ResponseStatusException:
    // Giữ nguyên HTTP status do Service chỉ định
    // Ví dụ:
    // 409 - Lịch làm việc bị chồng lấn
    // 404 - Không tìm thấy dữ liệu
    // 400 - Yêu cầu không hợp lệ
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(
            ResponseStatusException ex) {

        return buildErrorResponse(
                HttpStatus.valueOf(ex.getStatusCode().value()),
                ex.getReason() != null
                        ? ex.getReason()
                        : "Yêu cầu không thể thực hiện"
        );
    }

    // 4. Xử lý các RuntimeException khác
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage()
        );
    }

    // 5. Xử lý lỗi 400: JSON sai định dạng
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParseException(
            HttpMessageNotReadableException ex) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Lỗi cú pháp: Dữ liệu JSON gửi lên không đúng định dạng "
                        + "(có thể dư dấu phẩy hoặc sai ngoặc)."
        );
    }

    // 6. Xử lý lỗi 409: Vi phạm ràng buộc dữ liệu
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Lỗi xung đột dữ liệu: Mã bạn nhập đã tồn tại "
                        + "trong hệ thống hoặc vi phạm ràng buộc."
        );
    }

    // 7. Xử lý lỗi 404: Đường dẫn API không tồn tại
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(
            NoResourceFoundException ex) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Lỗi 404: Đường dẫn API không tồn tại, "
                        + "vui lòng kiểm tra lại URL."
        );
    }

    // Hàm dùng chung để trả về thông báo lỗi dạng JSON
    private ResponseEntity<Object> buildErrorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> error = new HashMap<>();

        error.put("thoi_gian", LocalDateTime.now());
        error.put("trang_thai", status.value());
        error.put("loi_chi_tiet", message);

        return new ResponseEntity<>(error, status);
    }
}
