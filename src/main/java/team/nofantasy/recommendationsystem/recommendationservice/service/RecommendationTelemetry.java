package team.nofantasy.recommendationsystem.recommendationservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.RecommendationListServiceDto;

import java.time.Duration;
import java.util.Locale;

@Component
public class RecommendationTelemetry {
    private final MeterRegistry registry;

    public RecommendationTelemetry(MeterRegistry registry) {
        this.registry = registry;
        for (String variant : new String[]{"llm", "baseline"}) {
            for (String engine : new String[]{"llm", "baseline"}) {
                for (String cache : new String[]{"hit", "miss"}) {
                    registry.counter("recommendation.responses", "variant", variant, "engine", engine, "cache", cache);
                }
            }
        }
        for (String reason : new String[]{"no_candidates", "invalid_ranking", "llm_error", "llm_unavailable"}) {
            registry.counter("recommendation.fallbacks", "reason", reason);
        }
        for (String outcome : new String[]{"success", "error"}) {
            registry.counter("recommendation.llm.calls", "outcome", outcome);
        }
        for (String direction : new String[]{"input", "output"}) {
            registry.counter("recommendation.llm.tokens", "direction", direction);
        }
    }

    public void served(RecommendationListServiceDto list, boolean cached, long elapsedNanos) {
        String engine = list.engine().name().toLowerCase(Locale.ROOT);
        registry.counter(
                "recommendation.responses",
                "variant", list.variant(),
                "engine", engine,
                "cache", cached ? "hit" : "miss"
        ).increment();
        registry.timer("recommendation.response.duration", "engine", engine)
                .record(Duration.ofNanos(elapsedNanos));
        registry.summary("recommendation.items", "engine", engine).record(list.items().size());
    }

    public void fallback(String reason) {
        registry.counter("recommendation.fallbacks", "reason", reason).increment();
    }

    public void llmCall(String outcome, long elapsedNanos) {
        registry.counter("recommendation.llm.calls", "outcome", outcome).increment();
        registry.timer("recommendation.llm.duration", "outcome", outcome)
                .record(Duration.ofNanos(elapsedNanos));
    }

    public void llmTokens(long input, long output) {
        if (input > 0) {
            registry.counter("recommendation.llm.tokens", "direction", "input").increment(input);
        }
        if (output > 0) {
            registry.counter("recommendation.llm.tokens", "direction", "output").increment(output);
        }
    }
}
