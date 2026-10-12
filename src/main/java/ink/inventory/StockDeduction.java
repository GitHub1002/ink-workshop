package ink.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_deductions")
public class StockDeduction {

  @Id
  @Column(name = "idempotency_key", length = 120)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "order_id")
  private Long orderId;

  @Column(nullable = false, length = 20)
  private String outcome;

  @Column(length = 200)
  private String reason;

  protected StockDeduction() {}

  public StockDeduction(String idempotencyKey, String fingerprint, long orderId, String outcome, String reason) {
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
    this.orderId = orderId;
    this.outcome = outcome;
    this.reason = reason;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getReason() {
    return reason;
  }
}
