package ink.workshop.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLineRequest(
    @NotNull(message = "必须指定商品") Long productId,
    @NotNull(message = "必须指定数量") @Min(value = 1, message = "数量至少为 1") Integer quantity) {}
