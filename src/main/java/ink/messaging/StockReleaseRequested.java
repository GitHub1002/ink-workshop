package ink.messaging;

import java.util.List;

public record StockReleaseRequested(
    long orderId,
    String idempotencyKey,
    List<Line> lines) {

  public record Line(long productId, String productName, int quantity) {}
}
