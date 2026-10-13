package ink.workshop.api;

import ink.workshop.catalog.CatalogUnavailableException;
import ink.workshop.catalog.ProductNotFoundException;
import ink.workshop.inventory.IdempotencyConflictException;
import ink.workshop.inventory.InsufficientStockException;
import ink.workshop.inventory.InventoryNotFoundException;
import ink.workshop.inventory.InventoryUnavailableException;
import ink.workshop.order.DuplicateProductException;
import ink.workshop.order.MissingIdempotencyKeyException;
import ink.workshop.order.OrderNotFoundException;
import ink.workshop.order.OrderNotPayableException;
import ink.workshop.user.EmailAlreadyUsedException;
import ink.workshop.user.InvalidCredentialsException;
import ink.workshop.user.PasswordConstraintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(InsufficientStockException.class)
  ResponseEntity<ApiError> insufficientStock(InsufficientStockException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("INSUFFICIENT_STOCK", exception.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ApiError> idempotencyConflict(IdempotencyConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("IDEMPOTENCY_CONFLICT", exception.getMessage()));
  }

  @ExceptionHandler(InventoryUnavailableException.class)
  ResponseEntity<ApiError> inventoryUnavailable(InventoryUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .body(new ApiError(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(MissingIdempotencyKeyException.class)
  ResponseEntity<ApiError> missingIdempotencyKey(MissingIdempotencyKeyException exception) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", exception.getMessage()));
  }

  @ExceptionHandler(CatalogUnavailableException.class)
  ResponseEntity<ApiError> catalogUnavailable(CatalogUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .body(new ApiError("CATALOG_UNAVAILABLE", exception.getMessage()));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ApiError> missingProduct(ProductNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiError("PRODUCT_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(InventoryNotFoundException.class)
  ResponseEntity<ApiError> missingInventory(InventoryNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiError("INVENTORY_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(OrderNotPayableException.class)
  ResponseEntity<ApiError> orderNotPayable(OrderNotPayableException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("ORDER_NOT_PAYABLE", exception.getMessage()));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ResponseEntity<ApiError> missingOrder(OrderNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiError("ORDER_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyUsedException.class)
  ResponseEntity<ApiError> emailAlreadyUsed(EmailAlreadyUsedException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("EMAIL_ALREADY_USED", exception.getMessage()));
  }

  @ExceptionHandler(PasswordConstraintException.class)
  ResponseEntity<ApiError> passwordConstraint(PasswordConstraintException exception) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", exception.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiError("INVALID_CREDENTIALS", exception.getMessage()));
  }

  @ExceptionHandler(DuplicateProductException.class)
  ResponseEntity<ApiError> duplicateProduct(DuplicateProductException exception) {
    return ResponseEntity.badRequest()
        .body(new ApiError("DUPLICATE_PRODUCT", exception.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> malformedJson(HttpMessageNotReadableException exception) {
    log.warn("malformed_json {}", exception.getMessage());
    return ResponseEntity.badRequest().body(new ApiError("MALFORMED_JSON", "请求体不是合法的 JSON"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .findFirst()
        .orElse("请求不合法");
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", message));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception) {
    log.error("unhandled_error", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "服务器内部错误"));
  }
}
