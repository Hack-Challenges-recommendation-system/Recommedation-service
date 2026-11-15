package team.nofantasy.recommendationsystem.catalogservice.controller.advice;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code GlobalExceptionHandler} is dedicated to centrally handle all uncaught runtime exceptions in the application.
 * Invalid request parameters are answered with 400 by Spring itself.
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings(value = {"unused"})
public class GlobalExceptionHandler {
    private void makeDefaultErrorLog(Exception e) {
        log.atError()
                .setMessage(e.getMessage())
                .setCause(e.getCause())
                .log();
    }

    @ExceptionHandler(exception = IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        makeDefaultErrorLog(e);
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(exception = EntityNotFoundException.class)
    public ResponseEntity<Void> handleEntityNotFoundException(EntityNotFoundException e) {
        makeDefaultErrorLog(e);
        return ResponseEntity.notFound().build();
    }
}
