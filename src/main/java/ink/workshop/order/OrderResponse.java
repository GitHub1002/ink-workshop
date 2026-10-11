package ink.workshop.order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
    long id,
    long userId,
    String status,
    int totalCents,
    Instant createdAt,
    List<OrderLineResponse> lines) {

  static OrderResponse from(Order order) {
    List<OrderLineResponse> lines = order.getLines().stream()
        .map(line -> new OrderLineResponse(
            line.getProductId(),
            line.getProductName(),
            line.getUnitPriceCents(),
            line.getQuantity()))
        .toList();
    return new OrderResponse(
        order.getId(),
        order.getUserId(),
        order.getStatus().name(),
        order.getTotalCents(),
        order.getCreatedAt(),
        lines);
  }
}
