package com.example.demo.task;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class TaskExceptionHandler {
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicateTask(DuplicateKeyException exception) {
        return problem(HttpStatus.CONFLICT, "A task with this taskId already exists");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        String fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .sorted()
                .reduce((first, second) -> first + ", " + second)
                .orElse("request");
        return problem(HttpStatus.BAD_REQUEST, "Invalid value for: " + fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class})
    ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Request contains invalid or malformed values");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
