package com.task.fieldnotes.common;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex) { return ProblemDetail.forStatusAndDetail(ex.status, ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail fields(MethodArgumentNotValidException ex) {
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields");
        problem.setProperty("errors",ex.getBindingResult().getFieldErrors().stream()
            .map(error -> java.util.Map.of("field",error.getField(),"message",
                error.getDefaultMessage()==null?"Invalid value":error.getDefaultMessage())).toList());
        return problem;
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ProblemDetail invalid(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields or parameters"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with an existing resource or relationship");
    }
}
