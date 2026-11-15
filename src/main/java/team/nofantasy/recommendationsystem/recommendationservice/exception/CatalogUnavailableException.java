package team.nofantasy.recommendationsystem.recommendationservice.exception;

/**
 * catalog-service can't answer and no snapshot of the catalog is cached, so there is nothing to recommend from.
 */
public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
