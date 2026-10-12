package ink.catalog;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

  private final CatalogService catalogService;

  public ProductController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public List<ProductResponse> list() {
    return catalogService.list();
  }

  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable long id) {
    return catalogService.get(id);
  }

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse product = catalogService.create(request);
    return ResponseEntity.created(URI.create("/products/" + product.id())).body(product);
  }
}
