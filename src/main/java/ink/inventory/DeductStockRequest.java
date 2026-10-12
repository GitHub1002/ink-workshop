package ink.inventory;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record DeductStockRequest(
    @NotBlank(message = "必须提供幂等键")
    @Size(max = 120, message = "幂等键最长 120 个字符")
    String idempotencyKey,
    @NotEmpty(message = "订单至少要有一件商品")
    List<@Valid DeductionLineRequest> lines) {}
