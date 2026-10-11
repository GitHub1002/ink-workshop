package ink.workshop.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record PlaceOrderRequest(
    @NotEmpty(message = "订单至少包含一行") List<@Valid OrderLineRequest> lines) {}
