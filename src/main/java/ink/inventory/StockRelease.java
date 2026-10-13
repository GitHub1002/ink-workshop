package ink.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_releases")
public class StockRelease {

  @Id
  @Column(name = "idempotency_key", length = 120)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "order_id", nullable = false)
  private long orderId;

  protected StockRelease() {}

  public StockRelease(String idempotencyKey, String fingerprint, long orderId) {
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
    this.orderId = orderId;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public long getOrderId() {
    return orderId;
  }
}
