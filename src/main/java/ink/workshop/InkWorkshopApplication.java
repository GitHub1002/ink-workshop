package ink.workshop;

import java.time.Clock;

import ink.messaging.StockTopology;
import ink.workshop.catalog.CatalogProperties;
import ink.workshop.inventory.InventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({InventoryProperties.class, CatalogProperties.class})
@EnableScheduling
@Import(StockTopology.class)
public class InkWorkshopApplication {

  public static void main(String[] args) {
    SpringApplication.run(InkWorkshopApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
   