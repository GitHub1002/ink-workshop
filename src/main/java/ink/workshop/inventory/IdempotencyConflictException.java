package ink.workshop.inventory;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException() {
    super("这个 Idempotency-Key 已经用于内容不同的请求");
  }
}
