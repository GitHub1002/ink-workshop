package ink.workshop.order;

public class MissingIdempotencyKeyException extends RuntimeException {

  public MissingIdempotencyKeyException() {
    super("下单必须提供 Idempotency-Key，长度不超过 80 个字符");
  }
}
