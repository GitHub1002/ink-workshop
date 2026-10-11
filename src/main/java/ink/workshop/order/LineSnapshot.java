package ink.workshop.order;

public record LineSnapshot(long productId, String productName, int unitPriceCents, int quantity) {}
