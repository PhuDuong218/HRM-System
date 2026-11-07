
package com.hrm.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PagingUtils {

    private PagingUtils() {
    }

    public static Pageable create(
            int page,
            int size,
            Sort sort) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page phải lớn hơn hoặc bằng 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size phải nằm trong khoảng 1 đến 100"
            );
        }

        return PageRequest.of(page, size, sort);
    }
}
