package com.swiftdrop.backend.modules.dispatch.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class DispatchExceptionHandler {

    @ExceptionHandler(AssignmentNotFoundException.class)
    public ProblemDetail handleAssignmentNotFound(AssignmentNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Assignment Not Found");
        return problem;
    }

    @ExceptionHandler(DuplicateAssignmentException.class)
    public ProblemDetail handleDuplicateAssignment(DuplicateAssignmentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Active Assignment");
        return problem;
    }

    @ExceptionHandler(InvalidAssignmentStateException.class)
    public ProblemDetail handleInvalidAssignmentState(InvalidAssignmentStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Assignment State");
        return problem;
    }
}
