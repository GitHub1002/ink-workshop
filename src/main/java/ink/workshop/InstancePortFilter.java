package ink.workshop;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InstancePortFilter extends OncePerRequestFilter {

  private final String port;

  public InstancePortFilter(@Value("${server.port}") int port) {
    this.port = Integer.toString(port);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    response.setHeader("X-Instance-Port", port);
    filterChain.doFilter(request, response);
  }
}
