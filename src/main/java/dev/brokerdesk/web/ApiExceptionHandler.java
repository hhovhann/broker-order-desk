package dev.brokerdesk.web;

import dev.brokerdesk.service.ApiException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletionException;

/** RFC 7807 problem responses; no stack traces or internals leave the service. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) { return ProblemDetail.forStatusAndDetail(e.status, e.getMessage()); }

    @ExceptionHandler(CompletionException.class)
    ProblemDetail wrapped(CompletionException e) {
        if (e.getCause() instanceof ApiException a) return api(a);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception e) {
        String detail = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + " " + f.getDefaultMessage()).sorted().reduce((a, b) -> a + "; " + b).orElse("invalid request")
                : "malformed request body";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
