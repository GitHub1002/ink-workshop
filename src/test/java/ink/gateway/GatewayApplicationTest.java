package ink.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.config.name=gateway",
    "spring.cloud.nacos.discovery.enabled=false"
})
@AutoConfigureMockMvc
class GatewayApplicationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void healthCarriesARequestId() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void keepsTheRequestIdSuppliedByTheCaller() throws Exception {
    mockMvc.perform(get("/no-such-path").header("X-Request-Id", "phase5-demo"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Request-Id", "phase5-demo"));
  }
}
