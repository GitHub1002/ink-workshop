package ink.workshop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(name = "price_cents", nullable = false)
  private int priceCents;

  @Column(nullable = false, length = 400)
  private String description;

  protected Product() {}

  public static Product create(String name, int priceCents, String description) {
    Product product = new Product();
    product.name = name;
    product.priceCents = priceCents;
    product.description = description;
    return product;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getPriceCents() {
    return priceCents;
  }

  public String getDescription() {
    return description;
  }
}
