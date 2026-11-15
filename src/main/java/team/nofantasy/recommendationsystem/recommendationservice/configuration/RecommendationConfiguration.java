package team.nofantasy.recommendationsystem.recommendationservice.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.RecommendationProperties;
import team.nofantasy.recommendationsystem.recommendationservice.configuration.properties.ServicesProperties;

@Configuration
@EnableConfigurationProperties({RecommendationProperties.class, ServicesProperties.class})
public class RecommendationConfiguration {
}
