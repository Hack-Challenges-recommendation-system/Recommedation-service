package team.nofantasy.recommendationsystem.recommendationservice.configuration;

import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import team.nofantasy.recommendationsystem.recommendationservice.service.XrayTunnelService;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class AnthropicProxyConfiguration {
    @Bean
    AnthropicHttpClientBuilderCustomizer anthropicProxy(XrayTunnelService tunnel) {
        return builder -> builder.proxy(tunnel.proxy());
    }
}
