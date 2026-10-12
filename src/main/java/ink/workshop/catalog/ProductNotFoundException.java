package ink.workshop.catalog;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(String message) {
    super(message);
  }

  public ProductNotFoundException(long productId) {
    this("商品不存在: " + productId);
  }
}
