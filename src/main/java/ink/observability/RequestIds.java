package ink.observability;

import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public final class RequestIds {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";
  public static final int MAX_LENGTH = 80;

  private RequestIds() {}

  public static ClientHttpRequestInterceptor forward() {
    return (request, body, execution) -> {
      String requestId = MDC.get(MDC_KEY);
      if (requestId != null && !requestId.isBlank()) {
        request.getHeaders().set(HEADER, requestId);
      }
      return execution.execute(request, body);
    };
  }
}
