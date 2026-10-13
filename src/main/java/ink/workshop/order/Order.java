package ink.workshop.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "total_cents", nullable = false)
  private int totalCents;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "idempotency_key", length = 80)
  private String idempotencyKey;

  @Column(name = "line_fingerprint", length = 64)
  private String lineFingerprint;

  @Column(name = "payment_key", length = 80)
  private String paymentKey;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderLine> lines = new ArrayList<>();

  protected Order() {}

  public static Order place(
      long userId,
      List<LineSnapshot> snapshots,
      Instant createdAt,
      String idempotencyKey,
      String lineFingerprint) {
    Order order = new Order();
    order.userId = userId;
    order.status = OrderStatus.PENDING;
    order.createdAt = createdAt;
    order.idempotencyKey = idempotencyKey;
    order.lineFingerprint = lineFingerprint;
    int total = 0;
    for (LineSnapshot snapshot : snapshots) {
      int lineTotal = Math.multiplyExact(snapshot.unitPriceCents(), snapshot.quantity());
      total = Math.addExact(total, lineTotal);
      order.lines.add(new OrderLine(order, snapshot));
    }
    order.totalCents = total;
    return order;
  }

  public Long getId() {
    return id;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public boolean applyStockResult(OrderStatus target) {
    if (status == target || status != OrderStatus.PENDING) {
      return false;
    }
    if (target != OrderStatus.RESERVED && target != OrderStatus.REJECTED) {
      throw new IllegalArgumentException(target.name());
    }
    status = target;
    return true;
  }

  public String getPaymentKey() {
    return paymentKey;
  }

  public void markPaid(String paymentKey) {
    this.paymentKey = paymentKey;
    this.status = OrderStatus.PAID;
  }

  public void markReleasing(String paymentKey) {
    this.paymentKey = paymentKey;
    this.status = OrderStatus.RELEASING;
  }

  public boolean markCancelled() {
    if (status != OrderStatus.RELEASING) {
      return false;
    }
    status = OrderStatus.CANCELLED;
    return true;
  }

  public int getTotalCents() {
    return totalCents;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getUserId() {
    return userId;
  }

  public String getLineFingerprint() {
    return lineFingerprint;
  }

  public List<OrderLine> getLines() {
    return List.copyOf(lines);
  }
}
