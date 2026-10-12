package ink.catalog;

import java.util.Map;

import ink.observability.ObservabilityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class,
    RabbitAutoConfiguration.class
})
@Import(ObservabilityConfiguration.class)
public class CatalogApplication {

  public static void main(String[] args) {
    start(args);
  }

  public static ConfigurableApplicationContext start(String... args) {
    SpringApplication application = new SpringApplication(CatalogApplication.class);
    application.setDefaultProperties(Map.of("spring.config.name", "catalog"));
    return application.run(args);
  }
}
