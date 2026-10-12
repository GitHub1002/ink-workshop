package ink.workshop.catalog;

import java.util.List;

import ink.workshop.inventory.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

  private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

  private final CatalogClient catalogClient;
  private final InventoryClient inventoryClient;

  public CatalogService(CatalogClient catalogClient, InventoryClient inventoryClient) {
    this.catalogClient = catalogClient;
    this.inventoryClient = inventoryClient;
  }

  public List<ProductResponse> list() {
    return catalogClient.list();
  }

  public ProductResponse get(long productId) {
    return catalogClient.get(productId);
  }

  public ProductResponse create(CreateProductRequest request) {
    ProductResponse saved = catalogClient.create(request);
    try {
      inventoryClient.initialize(saved.id());
    } catch (RuntimeException exception) {
      log.error("product_saved_without_stock productId={}", saved.id(), exception);
      throw exception;
    }
    return saved;
  }
}
