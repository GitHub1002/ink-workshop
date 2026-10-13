package ink.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

  private final InventoryRepository inventoryRepository;

  public InventoryService(InventoryRepository inventoryRepository) {
    this.inventoryRepository = inventoryRepository;
  }

  @Transactional
  public void initialize(long productId) {
    if (inventoryRepository.existsById(productId)) {
      return;
    }
    inventoryRepository.save(Inventory.open(productId));
  }

  @Transactional
  public InventoryResponse setAvailable(long productId, int available) {
    Inventory inventory = inventoryRepository.findById(productId)
        .orElseThrow(() -> new InventoryNotFoundException(productId));
    inventory.setAvailable(available);
    return InventoryResponse.from(inventory);
  }

  @Transactional(readOnly = true)
  public InventoryResponse get(long productId) {
    return inventoryRepository.findById(productId)
        .map(InventoryResponse::from)
        .orElseThrow(() -> new InventoryNotFoundException(productId));
  }

  @Transactional
  public void restore(long productId, int quantity) {
    int updated = inventoryRepository.addAvailable(productId, quantity);
    if (updated != 1) {
      throw new IllegalStateException("没有这块库存: " + productId);
    }
  }

  @Transactional
  public void deduct(long productId, String productName, int quantity) {
    Inventory inventory = inventoryRepository.findById(productId)
        .orElseThrow(() -> new InsufficientStockException(productName, quantity, 0));
    int updated = inventoryRepository.deductIfEnough(productId, quantity);
    if (updated != 1) {
      log.warn("insufficient_stock productId={} requested={} available={}",
          productId, quantity, inventory.getAvailable());
      throw new InsufficientStockException(productName, quantity, inventory.getAvailable());
    }
  }
}
