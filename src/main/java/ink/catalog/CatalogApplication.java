package ink.catalog;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    RabbitAutoConfiguration.class
})
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
