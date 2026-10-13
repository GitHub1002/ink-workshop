package ink.workshop.order;

public record PayResult(OrderResponse order, boolean compensating) {}
