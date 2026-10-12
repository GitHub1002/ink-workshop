package ink.workshop.inventory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import ink.workshop.api.ApiError;
import ink.workshop.order.LineSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class InventoryClient {

  private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
  private static final String DEDUCTION_UNKNOWN =
      "库存服务没有在时限内返回，扣减结果未知。请用同一个 Idempotency-Key 重试";
  private static final String UNAVAILABLE = "库存服务没有在时限内返回";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private volatile String baseUrl;
  private volatile RestClient client;

  public InventoryClient(InventoryProperties properties) {
    this.connectTimeout = properties.connectTimeout();
    this.readTimeout = properties.readTimeout();
    useBaseUrl(properties.baseUrl());
  }

  public void useBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    this.client = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build();
  }

  public String baseUrl() {
    return baseUrl;
  }

  public void deduct(String idempotencyKey, List<LineSnapshot> lines) {
    List<DeductionLine> bodyLines = lines.stream()
        .map(line -> new DeductionLine(line.productId(), line.productName(), line.quantity()))
        .toList();
    send(() -> client.post()
        .uri("/deductions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DeductStockRequest(idempotencyKey, bodyLines)), DeductionResponse.class, true);
  }

  public void initialize(long productId) {
    send(() -> client.post().uri("/inventory/{productId}", productId), Void.class, false);
  }

  public InventoryResponse get(long productId) {
    return send(() -> client.get().uri("/inventory/{productId}", productId), InventoryResponse.class, false);
  }

  public InventoryResponse setAvailable(long productId, int available) {
    return send(
        () -> client.put()
            .uri("/inventory/{productId}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SetStockRequest(available)),
        InventoryResponse.class,
        false);
  }

  private <T> T send(
      Supplier<RestClient.RequestHeadersSpec<?>> request, Class<T> type, boolean deduction) {
    try {
      return request.get().exchange((httpRequest, response) -> read(response, type, deduction));
    } catch (ResourceAccessException exception) {
      throw unavailable(deduction, exception);
    }
  }

  private <T> T read(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, Class<T> type, boolean deduction)
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
    throw map(error, deduction);
  }

  private RuntimeException map(ApiError error, boolean deduction) {
    return switch (error.code()) {
      case "INSUFFICIENT_STOCK" -> new InsufficientStockException(error.message());
      case "INVENTORY_NOT_FOUND" -> new InventoryNotFoundException(error.message());
      case "IDEMPOTENCY_CONFLICT" -> new IdempotencyConflictException();
      default -> unavailable(deduction, new IllegalStateException(error.code() + " " + error.message()));
    };
  }

  private InventoryUnavailableException unavailable(boolean deduction, Exception cause) {
    String message = deduction ? DEDUCTION_UNKNOWN : UNAVAILABLE;
    log.error("inventory_unavailable deduction={} baseUrl={}", deduction, baseUrl, cause);
    String code = deduction ? "STOCK_DEDUCTION_UNKNOWN" : "STOCK_SERVICE_UNAVAILABLE";
    return new InventoryUnavailableException(code, message);
  }

  private record DeductStockRequest(String idempotencyKey, List<DeductionLine> lines) {}

  private record DeductionLine(long productId, String productName, int quantity) {}

  private record DeductionResponse(String status) {}
}
