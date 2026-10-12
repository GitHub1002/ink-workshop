package ink.messaging;

public record StockDeductionResult(long orderId, String status, String reason) {}
