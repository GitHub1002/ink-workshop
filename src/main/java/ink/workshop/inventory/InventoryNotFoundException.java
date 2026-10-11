package ink.workshop.inventory;

public class InventoryNotFoundException extends RuntimeException {

  public InventoryNotFoundException(long productId) {
    super("库存不存在: " + productId);
  }
}
