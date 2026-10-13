package ink.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.config.name=gateway",
    "spring.cloud.nacos.discovery.enabled=false",
    "ink.gateway.rate-limit.permits=2",
    "ink.gateway.rate-limit.period=1m"
})
@AutoConfigureMockMvc
class ShopProtectionTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void rejectsShopTrafficPastTheLimit() throws Exception {
    mockMvc.perform(get("/products")).andReturn();
    mockMvc.perform(get("/products")).andReturn();

    mockMvc.perform(get("/products"))
        .andExpect(status().is(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void leavesHealthOutsideTheLimit() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
