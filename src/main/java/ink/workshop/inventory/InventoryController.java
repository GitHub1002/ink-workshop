package ink.workshop.inventory;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private final InventoryService inventoryService;

  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @GetMapping("/{productId}")
  public InventoryResponse get(@PathVariable long productId) {
    return inventoryService.get(productId);
  }

  @PutMapping("/{productId}")
  public InventoryResponse setAvailable(
      @PathVariable long productId, @Valid @RequestBody SetStockRequest request) {
    return inventoryService.setAvailable(productId, request.available());
  }
}
