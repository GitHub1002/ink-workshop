package ink.messaging;

import java.util.List;

public record StockDeductionRequested(
    long orderId,
    String idempotencyKey,
    List<Line> lines) {

  public record Line(long productId, String productName, int quantity) {}
}
