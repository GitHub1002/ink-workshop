package ink.workshop.catalog;

import java.util.List;

import ink.workshop.inventory.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

  private final ProductRepository productRepository;
  private final InventoryClient inventoryClient;

  public CatalogService(ProductRepository productRepository, InventoryClient inventoryClient) {
    this.productRepository = productRepository;
    this.inventoryClient = inventoryClient;
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> list() {
    return productRepository.findAllByOrderByIdAsc().stream()
        .map(ProductResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse get(long productId) {
    return productRepository.findById(productId)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ProductNotFoundException(productId));
  }

  public ProductResponse create(CreateProductRequest request) {
    Product saved = productRepository.save(Product.create(
        request.name().trim(), request.priceCents(), request.description().trim()));
    try {
      inventoryClient.initialize(saved.getId());
    } catch (RuntimeException exception) {
      log.error("product_saved_without_stock productId={}", saved.getId(), exception);
      throw exception;
    }
    return ProductResponse.from(saved);
  }
}
