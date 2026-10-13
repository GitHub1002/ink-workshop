package ink.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ShopRateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> SHOP_PREFIXES = Set.of(
      "/users", "/sessions", "/orders", "/products", "/inventory");

  private final RateLimiter limiter;

  public ShopRateLimitFilter(
      @Value("${ink.gateway.rate-limit.permits:20}") int permits,
      @Value("${ink.gateway.rate-limit.period:1s}") Duration period) {
    this.limiter = RateLimiter.of("shop", RateLimiterConfig.custom()
        .limitForPeriod(permits)
        .limitRefreshPeriod(period)
        .timeoutDuration(Duration.ZERO)
        .build());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String prefix : SHOP_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (limiter.acquirePermission()) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}");
  }
}
