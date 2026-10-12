package ink.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private final ProductRepository productRepository;

  public CatalogService(ProductRepository productRepository) {
    this.productRepository = productRepository;
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

  @Transactional
  public ProductResponse create(CreateProductRequest request) {
    Product saved = productRepository.save(Product.create(
        request.name().trim(), request.priceCents(), request.description().trim()));
    return ProductResponse.from(saved);
  }
}
