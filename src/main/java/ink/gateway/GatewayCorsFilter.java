package ink.gateway;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayCorsFilter extends OncePerRequestFilter {

  private final Set<String> allowedOrigins;

  public GatewayCorsFilter(
      @Value("${ink.gateway.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
    this.allowedOrigins = Set.of(origins.split(","));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String origin = request.getHeader("Origin");
    if (origin != null && allowedOrigins.contains(origin)) {
      response.setHeader("Access-Control-Allow-Origin", origin);
      response.setHeader("Vary", "Origin");
      response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
      response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key, X-Request-Id");
      response.setHeader("Access-Control-Expose-Headers", "X-Request-Id, X-Instance-Port, Location");
    }
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
