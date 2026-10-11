package ink.workshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrderTest {

  @Test
  void sumsLinePricesIntoTheOrderTotal() {
    Order order = Order.place(7L, List.of(
        new LineSnapshot(1, "松烟墨", 6800, 2),
        new LineSnapshot(3, "宣纸", 3600, 1)),
        Instant.parse("2026-10-10T12:00:00Z"));

    assertThat(order.getUserId()).isEqualTo(7L);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    assertThat(order.getTotalCents()).isEqualTo(17200);
    assertThat(order.getLines()).hasSize(2);
    assertThat(order.getCreatedAt()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
  }
}
