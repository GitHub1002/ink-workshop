package ink.workshop.order;

public class DuplicateProductException extends RuntimeException {

  public DuplicateProductException(long productId) {
    super("同一订单里商品不能重复: " + productId);
  }
}
