package ink.workshop.inventory;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

import ink.observability.RequestIds;
import ink.workshop.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class InventoryClient {

  private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
  private static final String UNAVAILABLE = "库存服务没有在时限内返回";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ObjectProvider<RestClient.Builder> clientBuilders;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private volatile String baseUrl;
  private volatile RestClient client;

  public InventoryClient(InventoryProperties properties, ObjectProvider<RestClient.Builder> clientBuilders) {
    this.clientBuilders = clientBuilders;
    this.connectTimeout = properties.connectTimeout();
    this.readTimeout = properties.readTimeout();
    useBaseUrl(properties.baseUrl());
  }

  public void useBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    this.client = clientBuilders.getObject()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(RequestIds.forward())
        .build();
  }

  public String baseUrl() {
    return baseUrl;
  }

  public void initialize(long productId) {
    send(() -> client.post().uri("/inventory/{productId}", productId), Void.class);
  }

  public InventoryResponse get(long productId) {
    return send(() -> client.get().uri("/inventory/{productId}", productId), InventoryResponse.class);
  }

  public InventoryResponse setAvailable(long productId, int available) {
    return send(
        () -> client.put()
            .uri("/inventory/{productId}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SetStockRequest(available)),
        InventoryResponse.class);
  }

  private <T> T send(Supplier<RestClient.RequestHeadersSpec<?>> request, Class<T> type) {
    try {
      return request.get().exchange((httpRequest, response) -> read(response, type));
    } catch (ResourceAccessException exception) {
      throw unavailable(exception);
    }
  }

  private <T> T read(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, Class<T> type)
      throws IOException {
    byte[] payload = response.getBody().readAllBytes();
    if (response.getStatusCode().is2xxSuccessful()) {
      if (type == Void.class || payload.length == 0) {
        return null;
      }
      return jsonMapper.readValue(payload, type);
    }
    ApiError error = payload.length == 0
        ? new ApiError("INTERNAL_ERROR", "库存服务调用失败")
        : jsonMapper.readValue(payload, ApiError.class);
    throw map(error);
  }

  private RuntimeException map(ApiError error) {
    return switch (error.code()) {
      case "INSUFFICIENT_STOCK" -> new InsufficientStockException(error.message());
      case "INVENTORY_NOT_FOUND" -> new InventoryNotFoundException(error.message());
      case "IDEMPOTENCY_CONFLICT" -> new IdempotencyConflictException();
      default -> unavailable(new IllegalStateException(error.code() + " " + error.message()));
    };
  }

  private InventoryUnavailableException unavailable(Exception cause) {
    log.error("inventory_unavailable baseUrl={}", baseUrl, cause);
    return new InventoryUnavailableException("STOCK_SERVICE_UNAVAILABLE", UNAVAILABLE);
  }
}
