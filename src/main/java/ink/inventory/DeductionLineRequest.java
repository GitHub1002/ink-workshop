package ink.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeductionLineRequest(
    @NotNull(message = "必须指定商品")
    Long productId,
    @NotBlank(message = "必须提供商品名称")
    @Size(max = 80, message = "商品名称最长 80 个字符")
    String productName,
    @NotNull(message = "必须指定数量")
    @Min(value = 1, message = "数量至少为 1")
    Integer quantity) {}
