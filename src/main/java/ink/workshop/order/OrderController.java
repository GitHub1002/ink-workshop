package ink.workshop.order;

import java.net.URI;
import java.util.List;

import ink.workshop.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final CurrentUser currentUser;

  public OrderController(OrderService orderService, CurrentUser currentUser) {
    this.orderService = orderService;
    this.currentUser = currentUser;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
    OrderResponse order = orderService.place(currentUser.id(), request);
    return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
  }

  @GetMapping
  public List<OrderResponse> list() {
    return orderService.list(currentUser.id());
  }

  @GetMapping("/{id}")
  public OrderResponse get(@PathVariable long id) {
    return orderService.get(currentUser.id(), id);
  }
}
