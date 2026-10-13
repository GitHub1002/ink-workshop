package ink.workshop.order;

public class OrderNotPayableException extends RuntimeException {

  public OrderNotPayableException() {
    super("订单当前不能付款");
  }
}
