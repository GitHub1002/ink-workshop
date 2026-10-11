package ink.workshop.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetStockRequest(
    @NotNull(message = "必须指定可售数量")
    @Min(value = 0, message = "可售数量不能为负")
    Integer available) {}
