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

  protected StockDeduction() {}

  public StockDeduction(String idempotencyKey, String fingerprint) {
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
  }

  public String getFingerprint() {
    return fingerprint;
  }
}
