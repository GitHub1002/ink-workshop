package ink.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

import ink.messaging.StockDeductionResult;
import ink.messaging.StockReleaseRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReleaseService {

  private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

  private final InventoryService inventoryService;
  private final StockReleaseRepository stockReleaseRepository;
  private final OutboxRepository outboxRepository;
  private final TransactionTemplate transactions;
  private final JsonMapper jsonMapper;

  public ReleaseService(
      InventoryService inventoryService,
      StockReleaseRepository stockReleaseRepository,
      OutboxRepository outboxRepository,
      TransactionTemplate transactions,
      JsonMapper jsonMapper) {
    this.inventoryService = inventoryService;
    this.stockReleaseRepository = stockReleaseRepository;
    this.outboxRepository = outboxRepository;
    this.transactions = transactions;
    this.jsonMapper = jsonMapper;
  }

  public void accept(StockReleaseRequested request) {
    String fingerprint = fingerprint(request);
    try {
      transactions.executeWithoutResult(status -> apply(request, fingerprint));
    } catch (DataIntegrityViolationException exception) {
      transactions.executeWithoutResult(status -> replay(request, fingerprint));
    }
  }

  private void apply(StockReleaseRequested request, String fingerprint) {
    Optional<StockRelease> existing = stockReleaseRepository.findById(request.idempotencyKey());
    if (existing.isPresent()) {
      ensureSameRequest(existing.get(), fingerprint);
      enqueueResult(existing.get());
      return;
    }
    for (StockReleaseRequested.Line line : request.lines()) {
      inventoryService.restore(line.productId(), line.quantity());
    }
    StockRelease saved = stockReleaseRepository.saveAndFlush(
        new StockRelease(request.idempotencyKey(), fingerprint, request.orderId()));
    enqueueResult(saved);
    log.info("stock_released key={} orderId={}", request.idempotencyKey(), request.orderId());
  }

  private void replay(StockReleaseRequested request, String fingerprint) {
    StockRelease existing = stockReleaseRepository.findById(request.idempotencyKey())
        .orElseThrow(IdempotencyConflictException::new);
    ensureSameRequest(existing, fingerprint);
    enqueueResult(existing);
  }

  private void enqueueResult(StockRelease release) {
    StockDeductionResult result = new StockDeductionResult(release.getOrderId(), "RELEASED", null);
    outboxRepository.save(new OutboxMessage(
        "STOCK_RELEASE_RESULT", jsonMapper.writeValueAsString(result), Instant.now()));
  }

  private void ensureSameRequest(StockRelease existing, String fingerprint) {
    if (!existing.getFingerprint().equals(fingerprint)) {
      throw new IdempotencyConflictException();
    }
  }

  private String fingerprint(StockReleaseRequested request) {
    String canonical = request.lines().stream()
        .sorted(Comparator.comparing(StockReleaseRequested.Line::productId))
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
