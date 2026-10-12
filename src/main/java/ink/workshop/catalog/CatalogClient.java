package ink.workshop.catalog;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CatalogClient {

  private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
  private static final String UNAVAILABLE = "商品服务没有在时限内返回";
  private static final TypeReference<List<ProductResponse>> PRODUCT_LIST = new TypeReference<>() {};

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ObjectProvider<RestClient.Builder> clientBuilders;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private volatile String baseUrl;
  private volatile RestClient client;

  public CatalogClient(CatalogProperties properties, ObjectProvider<RestClient.Builder> clientBuilders) {
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

  public List<ProductResponse> list() {
    byte[] payload = send(() -> client.get().uri("/products"));
    try {
      return jsonMapper.readValue(payload, PRODUCT_LIST);
    } catch (Exception exception) {
      throw unavailable(exception);
    }
  }

  public ProductResponse get(long productId) {
    byte[] payload = send(() -> client.get().uri("/products/{id}", productId));
    return read(payload, ProductResponse.class);
  }

  public ProductResponse create(CreateProductRequest request) {
    byte[] payload = send(() -> client.post()
        .uri("/products")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request));
    return read(payload, ProductResponse.class);
  }

  private byte[] send(Supplier<RestClient.RequestHeadersSpec<?>> request) {
    try {
      return request.get().exchange((httpRequest, response) -> readBody(response));
    } catch (ResourceAccessException exception) {
      throw unavailable(exception);
    }
  }

  private byte[] readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
    byte[] payload = response.getBody().readAllBytes();
    if (response.getStatusCode().is2xxSuccessful()) {
      return payload;
    }
    ApiError error = payload.length == 0
        ? new ApiError("INTERNAL_ERROR", "商品服务调用失败")
        : jsonMapper.readValue(payload, ApiError.class);
    if ("PRODUCT_NOT_FOUND".equals(error.code())) {
      throw new ProductNotFoundException(error.message());
    }
    throw unavailable(new IllegalStateException(error.code() + " " + error.message()));
  }

  private <T> T read(byte[] payload, Class<T> type) {
    try {
      return jsonMapper.readValue(payload, type);
    } catch (RuntimeException exception) {
      throw unavailable(exception);
    }
  }

  private CatalogUnavailableException unavailable(Exception cause) {
    log.error("catalog_unavailable baseUrl={}", baseUrl, cause);
    return new CatalogUnavailableException(UNAVAILABLE);
  }
}
