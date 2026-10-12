package ink.workshop.inventory;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ink.inventory")
public record InventoryProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
