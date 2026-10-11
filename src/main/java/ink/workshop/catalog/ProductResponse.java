package ink.workshop.catalog;

public record ProductResponse(long id, String name, int priceCents, String description) {

  static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(), product.getName(), product.getPriceCents(), product.getDescription());
  }
}
