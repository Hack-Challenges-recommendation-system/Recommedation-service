package team.nofantasy.recommendationsystem.recommendationservice.controller.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team.nofantasy.recommendationsystem.recommendationservice.exception.CatalogUnavailableException;

/**
 * {@code GlobalExceptionHandler} is dedicated to centrally handle all uncaught runtime exceptions in the application.
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

    /**
     * The frontend treats 503 as "recommendations unavailable" and falls back to catalog search.
     */
    @ExceptionHandler(exception = CatalogUnavailableException.class)
    public ResponseEntity<Void> handleCatalogUnavailableException(CatalogUnavailableException e) {
        makeDefaultErrorLog(e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
