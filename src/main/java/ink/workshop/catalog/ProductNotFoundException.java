package ink.workshop.catalog;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(long productId) {
    super("商品不存在: " + productId);
  }
}
