package ink.workshop.catalog;

public class CatalogUnavailableException extends RuntimeException {

  public CatalogUnavailableException(String message) {
    super(message);
  }
}
