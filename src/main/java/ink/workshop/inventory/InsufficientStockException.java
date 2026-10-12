package ink.workshop.inventory;

public class InsufficientStockException extends RuntimeException {

  public InsufficientStockException(String message) {
    super(message);
  }

  public InsufficientStockException(String productName, int requested, int available) {
    super("%s库存不足，需要 %d，可售 %d".formatted(productName, requested, available));
  }
}
