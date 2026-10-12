package ink.inventory;

import java.time.Clock;
import java.util.Map;

import ink.messaging.StockTopology;
import ink.observability.ObservabilityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@EnableScheduling
@Import({StockTopology.class, ObservabilityConfiguration.class})
public class InventoryApplication {

  public static void main(String[] args) {
    start(args);
  }

  public static ConfigurableApplicationContext start(String... args) {
    SpringApplication application = new SpringApplication(InventoryApplication.class);
    application.setDefaultProperties(Map.of("spring.config.name", "inventory"));
    return application.run(args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
