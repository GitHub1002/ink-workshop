package ink.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
    @NotBlank(message = "必须填写商品名称")
    @Size(max = 80, message = "商品名称最长 80 个字符")
    String name,
    @NotNull(message = "必须填写单价")
    @Min(value = 1, message = "单价至少为 1 分")
    Integer priceCents,
    @NotBlank(message = "必须填写简介")
    @Size(max = 400, message = "简介最长 400 个字符")
    String description) {}
