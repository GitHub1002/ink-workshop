package ink.workshop.catalog;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ink.catalog")
public record CatalogProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
