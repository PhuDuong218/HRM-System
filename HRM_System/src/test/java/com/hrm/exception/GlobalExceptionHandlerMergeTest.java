package com.hrm.exception;

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMergeTest {

    private FailureController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new FailureController();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    static Stream<Arguments> errorsFromBothBranches() {
        return Stream.of(
                Arguments.of(new BadCredentialsException("wrong password"), 401),
                Arguments.of(new AccessDeniedException("forbidden"), 403),
                Arguments.of(new IllegalArgumentException("invalid input"), 400),
                Arguments.of(new ResourceNotFoundException("missing employee"), 404),
                Arguments.of(new BusinessException(HttpStatus.CONFLICT, "overlap"), 409),
                Arguments.of(new ResponseStatusException(HttpStatusCode.valueOf(499), "custom status"), 499),
                Arguments.of(new ConcurrencyFailureException("concurrent update"), 409),
                Arguments.of(sqlFailure(51100), 409),
                Arguments.of(sqlFailure(51199), 409),
                Arguments.of(sqlFailure(51099), 500),
                Arguments.of(sqlFailure(51200), 500));
    }

    @ParameterizedTest
    @MethodSource("errorsFromBothBranches")
    void dispatchesSpecificHandlersFromBothBranches(RuntimeException failure, int expectedStatus)
            throws Exception {
        controller.failure = failure;

        mvc.perform(get("/merge-test/failure"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.trang_thai").value(expectedStatus))
                .andExpect(jsonPath("$.thoi_gian").exists())
                .andExpect(jsonPath("$.loi_chi_tiet").isString());
    }

    @Test
    void retainsPersonalJsonParseMessage() throws Exception {
        mvc.perform(post("/merge-test/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invalid\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.loi_chi_tiet").value(containsString("Lỗi cú pháp")));
    }

    @Test
    void handlesMainMethodParameterValidationWithoutLosingTheErrorFormat() throws Exception {
        mvc.perform(get("/merge-test/validate").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trang_thai").value(400))
                .andExpect(jsonPath("$.loi_chi_tiet").value("Tham số yêu cầu không hợp lệ"));
    }

    @Test
    void preservesInternalServerErrorForInvalidControllerReturnValues() throws Exception {
        mvc.perform(get("/merge-test/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.trang_thai").value(500))
                .andExpect(jsonPath("$.loi_chi_tiet").value("Tham số yêu cầu không hợp lệ"));
    }

    @Test
    void retainsMainFallbackWithoutExposingInternalFailure() throws Exception {
        controller.failure = new RuntimeException("sensitive internal details");

        mvc.perform(get("/merge-test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.loi_chi_tiet").value("Máy chủ không xử lý được yêu cầu"))
                .andExpect(content().string(not(containsString("sensitive internal details"))));
    }

    private static UncategorizedSQLException sqlFailure(int code) {
        return new UncategorizedSQLException("test", "", new SQLException("SQL failure", "S0001", code));
    }

    @RestController
    static class FailureController {
        private RuntimeException failure;

        @GetMapping("/merge-test/failure")
        public String fail() {
            throw failure;
        }

        @PostMapping("/merge-test/json")
        public Map<String, Object> readJson(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/merge-test/validate")
        public String validate(@RequestParam @Min(1) int count) {
            return Integer.toString(count);
        }

        @GetMapping("/merge-test/invalid-return")
        @NotBlank
        public String invalidReturn() {
            return "";
        }
    }
}
