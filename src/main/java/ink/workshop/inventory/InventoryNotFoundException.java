package ink.workshop.inventory;

public class InventoryNotFoundException extends RuntimeException {

  public InventoryNotFoundException(String message) {
    super(message);
  }

  public InventoryNotFoundException(long productId) {
    this("库存不存在: " + productId);
  }
}
