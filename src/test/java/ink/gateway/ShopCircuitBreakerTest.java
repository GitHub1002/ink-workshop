package ink.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.config.name=gateway",
    "spring.cloud.nacos.discovery.enabled=false",
    "ink.gateway.rate-limit.permits=100",
    "resilience4j.circuitbreaker.instances.shop.sliding-window-size=4",
    "resilience4j.circuitbreaker.instances.shop.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.shop.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.shop.wait-duration-in-open-state=30s"
})
@AutoConfigureMockMvc
class ShopCircuitBreakerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private Resilience4JCircuitBreakerFactory circuitBreakers;

  @Test
  void opensTheCircuitAfterRepeatedShopFailures() throws Exception {
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(get("/products")).andExpect(forwardedUrl("/fallback/shop"));
    }

    assertThat(circuitBreakers.getCircuitBreakerRegistry().circuitBreaker("shop").getState()).isEqualTo(State.OPEN);
    mockMvc.perform(get("/products")).andExpect(forwardedUrl("/fallback/shop"));
  }
}
