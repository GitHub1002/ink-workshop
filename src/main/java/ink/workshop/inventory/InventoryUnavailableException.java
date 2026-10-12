package ink.workshop.inventory;

public class InventoryUnavailableException extends RuntimeException {

  private final String code;

  public InventoryUnavailableException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
