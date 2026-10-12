package ink.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeductionService {

  private static final Logger log = LoggerFactory.getLogger(DeductionService.class);

  private final InventoryService inventoryService;
  private final StockDeductionRepository stockDeductionRepository;
  private final TransactionTemplate transactions;

  public DeductionService(
      InventoryService inventoryService,
      StockDeductionRepository stockDeductionRepository,
      TransactionTemplate transactions) {
    this.inventoryService = inventoryService;
    this.stockDeductionRepository = stockDeductionRepository;
    this.transactions = transactions;
  }

  public void deduct(DeductStockRequest request) {
    rejectDuplicateProducts(request);
    try {
      transactions.executeWithoutResult(status -> apply(request));
    } catch (DataIntegrityViolationException exception) {
      transactions.executeWithoutResult(status -> replay(request));
    }
  }

  private void apply(DeductStockRequest request) {
    String fingerprint = fingerprint(request);
    Optional<StockDeduction> existing = stockDeductionRepository.findById(request.idempotencyKey());
    if (existing.isPresent()) {
      ensureSameRequest(existing.get(), fingerprint);
      return;
    }
    for (DeductionLineRequest line : request.lines()) {
      inventoryService.deduct(line.productId(), line.productName(), line.quantity());
    }
    stockDeductionRepository.saveAndFlush(new StockDeduction(request.idempotencyKey(), fingerprint));
    log.info("stock_deducted key={}", request.idempotencyKey());
  }

  private void replay(DeductStockRequest request) {
    StockDeduction existing = stockDeductionRepository.findById(request.idempotencyKey())
        .orElseThrow(() -> new IdempotencyConflictException());
    ensureSameRequest(existing, fingerprint(request));
  }

  private void ensureSameRequest(StockDeduction existing, String fingerprint) {
    if (!existing.getFingerprint().equals(fingerprint)) {
      throw new IdempotencyConflictException();
    }
  }

  private void rejectDuplicateProducts(DeductStockRequest request) {
    Set<Long> seen = new HashSet<>();
    for (DeductionLineRequest line : request.lines()) {
      if (!seen.add(line.productId())) {
        throw new DuplicateProductException(line.productId());
      }
    }
  }

  private String fingerprint(DeductStockRequest request) {
    String canonical = request.lines().stream()
        .sorted(Comparator.comparing(DeductionLineRequest::productId))
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
