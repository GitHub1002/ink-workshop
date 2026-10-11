package ink.workshop.order;

public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(long orderId) {
    super("订单不存在: " + orderId);
  }
}
