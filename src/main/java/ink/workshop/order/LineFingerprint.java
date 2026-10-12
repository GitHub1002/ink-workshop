package ink.workshop.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class LineFingerprint {

  private LineFingerprint() {}

  public static String of(List<OrderLineRequest> lines) {
    String canonical = lines.stream()
        .sorted(Comparator.comparing(OrderLineRequest::productId))
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
