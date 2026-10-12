package ink.workshop.order;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ink.messaging.StockDeductionRequested;
import ink.messaging.StockDeductionResult;
import ink.workshop.catalog.CatalogService;
import ink.workshop.catalog.ProductResponse;
import ink.workshop.inventory.IdempotencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final CatalogService catalogService;
  private final OrderRepository orderRepository;
  private final OutboxRepository outboxRepository;
  private final TransactionTemplate transactions;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public OrderService(
      CatalogService catalogService,
      OrderRepository orderRepository,
      OutboxRepository outboxRepository,
      TransactionTemplate transactions,
      JsonMapper jsonMapper,
      Clock clock) {
    this.catalogService = catalogService;
    this.orderRepository = orderRepository;
    this.outboxRepository = outboxRepository;
    this.transactions = transactions;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  public PlaceResult place(long userId, String idempotencyKey, PlaceOrderRequest request) {
    String fingerprint = LineFingerprint.of(request.lines());
    Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    if (existing.isPresent()) {
      return new PlaceResult(reuse(existing.get(), fingerprint), false);
    }
    List<LineSnapshot> snapshots = price(request.lines());
    try {
      Order saved = transactions.execute(status -> savePending(userId, snapshots, idempotencyKey, fingerprint));
      log.info("order_accepted id={} userId={} totalCents={}", saved.getId(), userId, saved.getTotalCents());
      return new PlaceResult(response(saved, snapshots), true);
    } catch (DataIntegrityViolationException exception) {
      return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
          .map(saved -> new PlaceResult(reuse(saved, fingerprint), false))
          .orElseThrow(() -> exception);
    }
  }

  @Transactional
  public void applyStockResult(StockDeductionResult result) {
    Order order = orderRepository.findById(result.orderId())
        .orElseThrow(() -> new IllegalStateException("订单 " + result.orderId() + " 还不存在"));
    OrderStatus target = OrderStatus.valueOf(result.status());
    if (order.applyStockResult(target)) {
      log.info("order_stock_result id={} status={}", order.getId(), target);
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

  private Order savePending(
      long userId, List<LineSnapshot> snapshots, String idempotencyKey, String fingerprint) {
    Order saved = orderRepository.saveAndFlush(
        Order.place(userId, snapshots, clock.instant(), idempotencyKey, fingerprint));
    List<StockDeductionRequested.Line> lines = snapshots.stream()
        .map(line -> new StockDeductionRequested.Line(line.productId(), line.productName(), line.quantity()))
        .toList();
    StockDeductionRequested event = new StockDeductionRequested(
        saved.getId(), userId + ":" + idempotencyKey, lines);
    outboxRepository.save(new OutboxMessage(
        "STOCK_DEDUCTION_REQUESTED", jsonMapper.writeValueAsString(event), clock.instant()));
    return saved;
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
