package team.nofantasy.recommendationsystem.catalogservice.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import team.nofantasy.recommendationsystem.catalogservice.configuration.properties.CatalogProperties;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfiguration {
}
