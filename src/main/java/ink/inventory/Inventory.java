package ink.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Column(nullable = false)
  private int available;

  protected Inventory() {}

  public static Inventory open(long productId) {
    Inventory inventory = new Inventory();
    inventory.productId = productId;
    inventory.available = 0;
    return inventory;
  }

  public void setAvailable(int available) {
    this.available = available;
  }

  public Long getProductId() {
    return productId;
  }

  public int getAvailable() {
    return available;
  }
}
