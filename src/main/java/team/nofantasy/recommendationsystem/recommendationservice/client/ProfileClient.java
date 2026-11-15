package team.nofantasy.recommendationsystem.recommendationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.ServicesProperties;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the business profile from profile-service on behalf of the user.
 */
@Component
@Slf4j
public class ProfileClient {
    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    public ProfileClient(RestClient.Builder restClientBuilder, ServicesProperties servicesProperties) {
        this.restClient = restClientBuilder.baseUrl(servicesProperties.profileUrl()).build();
    }

    /**
     * The profile of the user; empty if it doesn't exist yet or profile-service can't answer.
     */
    public Optional<ProfileServiceDto> getProfile(UUID userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/profile/me")
                    .header(USER_ID_HEADER, userId.toString())
                    .retrieve()
                    .body(ProfileServiceDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.atWarn()
                    .setMessage("Profile is unavailable; recommending without it")
                    .addKeyValue("userId", userId)
                    .addKeyValue("error", e.getMessage())
                    .log();
            return Optional.empty();
        }
    }
}
