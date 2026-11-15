package team.nofantasy.recommendationsystem.recommendationservice.exception;

/**
 * The LLM gave no usable answer: it isn't configured, the call failed or timed out, or the answer isn't the expected JSON.
 */
public class LlmRankingException extends RuntimeException {
    public LlmRankingException(String message) {
        super(message);
    }

    public LlmRankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
