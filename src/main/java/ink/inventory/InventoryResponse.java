package ink.inventory;

public record InventoryResponse(long productId, int available) {

  static InventoryResponse from(Inventory inventory) {
    return new InventoryResponse(inventory.getProductId(), inventory.getAvailable());
  }
}
