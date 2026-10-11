package ink.workshop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Inventory inventory
      set inventory.available = inventory.available - :quantity
      where inventory.productId = :productId
        and inventory.available >= :quantity
      """)
  int deductIfEnough(@Param("productId") long productId, @Param("quantity") int quantity);
}
