package team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation;

import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;

/**
 * A material the list may contain, with the signals that made it a candidate.
 *
 * @param topicMatch full-text match of the material with the goal of the user; 0 if it didn't match.
 * @param popularUsers distinct users who started or completed the material.
 * @param takenAfter title of a material in the user's history that people often take before this one, or {@code null}.
 * @param stageMatch the material is meant for the user's business stage.
 * @param formatMatch the material has a format the user prefers.
 */
public record CandidateServiceDto(
        MaterialServiceDto material,
        double topicMatch,
        long popularUsers,
        String takenAfter,
        boolean stageMatch,
        boolean formatMatch
) {
}
