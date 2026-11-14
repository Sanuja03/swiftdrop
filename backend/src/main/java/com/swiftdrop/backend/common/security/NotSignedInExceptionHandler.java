package com.swiftdrop.backend.common.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link NotSignedInException} to a 401 RFC 9457 problem for every controller,
 * ahead of the auth module's catch-all handler (which would turn it into a 500).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class NotSignedInExceptionHandler {

    @ExceptionHandler(NotSignedInException.class)
    public ProblemDetail handleNotSignedIn(NotSignedInException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Not signed in");
        return problem;
    }
}
