package ink.inventory;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException() {
    super("这个幂等键已经用于内容不同的扣库存请求");
  }
}
