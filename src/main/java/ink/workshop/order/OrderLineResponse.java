package ink.workshop.order;

public record OrderLineResponse(
    long productId, String productName, int unitPriceCents, int quantity) {}
