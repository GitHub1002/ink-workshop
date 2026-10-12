package ink.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import ink.messaging.StockDeductionRequested;
import ink.messaging.StockDeductionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DeductionService {

  private static final Logger log = LoggerFactory.getLogger(DeductionService.class);
  private static final String APPLIED = "APPLIED";
  private static final String REJECTED = "REJECTED";

  private final InventoryService inventoryService;
  private final StockDeductionRepository stockDeductionRepository;
  private final OutboxRepository outboxRepository;
  private final TransactionTemplate transactions;
  private final JsonMapper jsonMapper;

  public DeductionService(
      InventoryService inventoryService,
      StockDeductionRepository stockDeductionRepository,
      OutboxRepository outboxRepository,
      TransactionTemplate transactions,
      JsonMapper jsonMapper) {
    this.inventoryService = inventoryService;
    this.stockDeductionRepository = stockDeductionRepository;
    this.outboxRepository = outboxRepository;
    this.transactions = transactions;
    this.jsonMapper = jsonMapper;
  }

  public void accept(StockDeductionRequested request) {
    rejectDuplicateProducts(request);
    String fingerprint = fingerprint(request);
    try {
      transactions.executeWithoutResult(status -> apply(request, fingerprint));
    } catch (InsufficientStockException exception) {
      try {
        transactions.executeWithoutResult(status -> reject(request, fingerprint, exception.getMessage()));
      } catch (DataIntegrityViolationException conflict) {
        transactions.executeWithoutResult(status -> replay(request, fingerprint));
      }
    } catch (DataIntegrityViolationException exception) {
      transactions.executeWithoutResult(status -> replay(request, fingerprint));
    }
  }

  private void apply(StockDeductionRequested request, String fingerprint) {
    Optional<StockDeduction> existing = stockDeductionRepository.findById(request.idempotencyKey());
    if (existing.isPresent()) {
      ensureSameRequest(existing.get(), fingerprint);
      enqueueResult(existing.get());
      return;
    }
    for (StockDeductionRequested.Line line : request.lines()) {
      inventoryService.deduct(line.productId(), line.productName(), line.quantity());
    }
    StockDeduction saved = stockDeductionRepository.saveAndFlush(
        new StockDeduction(request.idempotencyKey(), fingerprint, request.orderId(), APPLIED, null));
    enqueueResult(saved);
    log.info("stock_deducted key={} orderId={}", request.idempotencyKey(), request.orderId());
  }

  private void reject(StockDeductionRequested request, String fingerprint, String reason) {
    Optional<StockDeduction> existing = stockDeductionRepository.findById(request.idempotencyKey());
    if (existing.isPresent()) {
      ensureSameRequest(existing.get(), fingerprint);
      enqueueResult(existing.get());
      return;
    }
    StockDeduction saved = stockDeductionRepository.saveAndFlush(
        new StockDeduction(request.idempotencyKey(), fingerprint, request.orderId(), REJECTED, reason));
    enqueueResult(saved);
    log.info("stock_rejected key={} orderId={} reason={}", request.idempotencyKey(), request.orderId(), reason);
  }

  private void replay(StockDeductionRequested request, String fingerprint) {
    StockDeduction existing = stockDeductionRepository.findById(request.idempotencyKey())
        .orElseThrow(IdempotencyConflictException::new);
    ensureSameRequest(existing, fingerprint);
    enqueueResult(existing);
  }

  private void enqueueResult(StockDeduction deduction) {
    if (deduction.getOrderId() == null) {
      return;
    }
    String status = APPLIED.equals(deduction.getOutcome()) ? "RESERVED" : "REJECTED";
    StockDeductionResult result = new StockDeductionResult(
        deduction.getOrderId(), status, deduction.getReason());
    outboxRepository.save(new OutboxMessage(
        "STOCK_DEDUCTION_RESULT", jsonMapper.writeValueAsString(result), Instant.now()));
  }

  private void ensureSameRequest(StockDeduction existing, String fingerprint) {
    if (!existing.getFingerprint().equals(fingerprint)) {
      throw new IdempotencyConflictException();
    }
  }

  private void rejectDuplicateProducts(StockDeductionRequested request) {
    if (request.lines() == null || request.lines().isEmpty()) {
      throw new IllegalArgumentException("扣库存消息没有商品");
    }
    Set<Long> seen = new HashSet<>();
    for (StockDeductionRequested.Line line : request.lines()) {
      if (!seen.add(line.productId())) {
        throw new DuplicateProductException(line.productId());
      }
    }
  }

  private String fingerprint(StockDeductionRequested request) {
    String canonical = request.lines().stream()
        .sorted(Comparator.comparing(StockDeductionRequested.Line::productId))
        .map(line -> line.productId() + ":" + line.quantity())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
