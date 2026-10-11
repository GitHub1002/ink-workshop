package ink.workshop.order;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ink.workshop.catalog.CatalogService;
import ink.workshop.catalog.ProductResponse;
import ink.workshop.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final CatalogService catalogService;
  private final InventoryService inventoryService;
  private final OrderRepository orderRepository;
  private final Clock clock;

  public OrderService(
      CatalogService catalogService,
      InventoryService inventoryService,
      OrderRepository orderRepository,
      Clock clock) {
    this.catalogService = catalogService;
    this.inventoryService = inventoryService;
    this.orderRepository = orderRepository;
    this.clock = clock;
  }

  @Transactional
  public OrderResponse place(long userId, PlaceOrderRequest request) {
    List<LineSnapshot> snapshots = price(request.lines());
    for (LineSnapshot snapshot : snapshots) {
      inventoryService.deduct(snapshot.productId(), snapshot.productName(), snapshot.quantity());
    }
    Order saved = orderRepository.save(Order.place(userId, snapshots, clock.instant()));
    log.info("order_placed id={} userId={} totalCents={}", saved.getId(), userId, saved.getTotalCents());
    return OrderResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public OrderResponse get(long userId, long orderId) {
    return orderRepository.findByIdAndUserId(orderId, userId)
        .map(OrderResponse::from)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> list(long userId) {
    return orderRepository.findAllByUserIdOrderByIdDesc(userId).stream()
        .map(OrderResponse::from)
        .toList();
  }

  private List<LineSnapshot> price(List<OrderLineRequest> lines) {
    Set<Long> seen = new HashSet<>();
    List<LineSnapshot> snapshots = new ArrayList<>();
    for (OrderLineRequest line : lines) {
      if (!seen.add(line.productId())) {
        throw new DuplicateProductException(line.productId());
      }
      ProductResponse product = catalogService.get(line.productId());
      snapshots.add(new LineSnapshot(
          product.id(), product.name(), product.priceCents(), line.quantity()));
    }
    return snapshots;
  }
}
