package team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The business profile from profile-service. Its user id and nickname are deliberately not read: these fields go to
 * the LLM, and nothing in them may identify the user.
 *
 * @param updatedAt when the answers last changed; part of the cache key.
 */
public record ProfileServiceDto(
        String businessStage,
        String businessSphere,
        String experience,
        String goal,
        List<String> preferredFormats,
        Instant updatedAt
) {
    /**
     * The profile of a user whose create event hasn't reached profile-service yet, or while profile-service is down.
     */
    public static final ProfileServiceDto EMPTY = new ProfileServiceDto(null, null, null, null, List.of(), null);

    public ProfileServiceDto {
        preferredFormats = preferredFormats == null
                ? List.of()
                : preferredFormats.stream().filter(Objects::nonNull).toList();
    }
}
