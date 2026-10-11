package ink.workshop.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_lines")
public class OrderLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private long productId;

  @Column(name = "product_name", nullable = false, length = 80)
  private String productName;

  @Column(name = "unit_price_cents", nullable = false)
  private int unitPriceCents;

  @Column(nullable = false)
  private int quantity;

  protected OrderLine() {}

  OrderLine(Order order, LineSnapshot snapshot) {
    this.order = order;
    this.productId = snapshot.productId();
    this.productName = snapshot.productName();
    this.unitPriceCents = snapshot.unitPriceCents();
    this.quantity = snapshot.quantity();
  }

  public long getProductId() {
    return productId;
  }

  public String getProductName() {
    return productName;
  }

  public int getUnitPriceCents() {
    return unitPriceCents;
  }

  public int getQuantity() {
    return quantity;
  }
}
