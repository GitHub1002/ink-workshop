package ink.workshop.order;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ink.workshop.catalog.CatalogService;
import ink.workshop.catalog.ProductResponse;
import ink.workshop.inventory.IdempotencyConflictException;
import ink.workshop.inventory.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final CatalogService catalogService;
  private final InventoryClient inventoryClient;
  private final OrderRepository orderRepository;
  private final Clock clock;

  public OrderService(
      CatalogService catalogService,
      InventoryClient inventoryClient,
      OrderRepository orderRepository,
      Clock clock) {
    this.catalogService = catalogService;
    this.inventoryClient = inventoryClient;
    this.orderRepository = orderRepository;
    this.clock = clock;
  }

  public OrderResponse place(long userId, String idempotencyKey, PlaceOrderRequest request) {
    String fingerprint = LineFingerprint.of(request.lines());
    Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    if (existing.isPresent()) {
      return reuse(existing.get(), fingerprint);
    }
    List<LineSnapshot> snapshots = price(request.lines());
    String inventoryKey = userId + ":" + idempotencyKey;
    inventoryClient.deduct(inventoryKey, snapshots);
    Order order = Order.place(userId, snapshots, clock.instant(), idempotencyKey, fingerprint);
    try {
      Order saved = orderRepository.save(order);
      log.info("order_placed id={} userId={} totalCents={}", saved.getId(), userId, saved.getTotalCents());
      return response(saved, snapshots);
    } catch (DataIntegrityViolationException exception) {
      return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
          .map(saved -> reuse(saved, fingerprint))
          .orElseThrow(() -> {
            log.error(
                "order_not_saved_after_stock_deduction userId={} key={}",
                userId,
                idempotencyKey,
                exception);
            return exception;
          });
    }
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

  private OrderResponse reuse(Order order, String fingerprint) {
    if (!fingerprint.equals(order.getLineFingerprint())) {
      throw new IdempotencyConflictException();
    }
    return OrderResponse.from(order);
  }

  private OrderResponse response(Order order, List<LineSnapshot> snapshots) {
    List<OrderLineResponse> lines = snapshots.stream()
        .map(line -> new OrderLineResponse(
            line.productId(), line.productName(), line.unitPriceCents(), line.quantity()))
        .toList();
    return new OrderResponse(
        order.getId(),
        order.getUserId(),
        order.getStatus().name(),
        order.getTotalCents(),
        order.getCreatedAt(),
        lines);
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
