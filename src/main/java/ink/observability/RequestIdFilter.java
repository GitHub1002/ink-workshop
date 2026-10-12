package ink.observability;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(RequestIds.HEADER);
    if (requestId == null || requestId.isBlank() || requestId.length() > RequestIds.MAX_LENGTH) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(RequestIds.MDC_KEY, requestId);
    response.setHeader(RequestIds.HEADER, requestId);
    try {
      filterChain.doFilter(new RequestIdRequest(request, requestId), response);
    } finally {
      response.setHeader(RequestIds.HEADER, requestId);
      MDC.remove(RequestIds.MDC_KEY);
    }
  }

  private static final class RequestIdRequest extends HttpServletRequestWrapper {

    private final String requestId;

    private RequestIdRequest(HttpServletRequest request, String requestId) {
      super(request);
      this.requestId = requestId;
    }

    @Override
    public String getHeader(String name) {
      if (RequestIds.HEADER.equalsIgnoreCase(name)) {
        return requestId;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (RequestIds.HEADER.equalsIgnoreCase(name)) {
        return Collections.enumeration(Set.of(requestId));
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>();
      Enumeration<String> existing = super.getHeaderNames();
      while (existing.hasMoreElements()) {
        names.add(existing.nextElement());
      }
      names.add(RequestIds.HEADER);
      return Collections.enumeration(names);
    }
  }
}
