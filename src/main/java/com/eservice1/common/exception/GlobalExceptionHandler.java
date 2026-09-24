package com.eservice1.common.exception;

import com.eservice1.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.HashMap;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(

            AccountLockedException ex

    ) {

        ErrorResponse error =

                new ErrorResponse(

                        LocalDateTime.now(),

                        HttpStatus.TOO_MANY_REQUESTS.value(),

                        "Too Many Requests",

                        ex.getMessage()

                );

        return ResponseEntity

                .status(HttpStatus.TOO_MANY_REQUESTS)

                .body(error);

    }@ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(

            InvalidCredentialsException ex

    ) {

        ErrorResponse error =

                new ErrorResponse(

                        LocalDateTime.now(),

                        HttpStatus.UNAUTHORIZED.value(),

                        "Unauthorized",

                        ex.getMessage()

                );

        return ResponseEntity

                .status(HttpStatus.UNAUTHORIZED)

                .body(error);
    }@ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse>
    handleNotFound(

                    ResourceNotFoundException ex

            ) {

        ErrorResponse error =

                new ErrorResponse(

                        LocalDateTime.now(),

                        HttpStatus.NOT_FOUND.value(),

                        "Not Found",

                        ex.getMessage()

                );

        return ResponseEntity

                .status(HttpStatus.NOT_FOUND)

                .body(error);

    }
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(

            DuplicateResourceException ex

    ) {

        ErrorResponse error = new ErrorResponse(

                LocalDateTime.now(),

                HttpStatus.CONFLICT.value(),

                "Conflict",

                ex.getMessage()

        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);

    }
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOperation(

            InvalidOperationException ex

    ) {

        ErrorResponse error = new ErrorResponse(

                LocalDateTime.now(),

                HttpStatus.BAD_REQUEST.value(),

                "Bad Request",

                ex.getMessage()

        );

        return ResponseEntity
                .badRequest()
                .body(error);

    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "A resource conflict occurred or unique constraint was violated."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(

            MethodArgumentNotValidException ex

    ) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .badRequest()
                .body(errors);

    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(

            AccessDeniedException ex

    ) {

        ErrorResponse error = new ErrorResponse(

                LocalDateTime.now(),

                HttpStatus.FORBIDDEN.value(),

                "Forbidden",

                ex.getMessage()

        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error);

    }
    @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            jakarta.persistence.OptimisticLockException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "The request was modified by another operation. Please retry."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(

            Exception ex

    ) {
        ex.printStackTrace();   // <-- add this

        ErrorResponse error = new ErrorResponse(

                LocalDateTime.now(),

                HttpStatus.INTERNAL_SERVER_ERROR.value(),

                "Internal Server Error",

                "Something went wrong. Please try again later."

        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);

    }

}