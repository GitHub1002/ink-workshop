package ink.workshop.order;

import ink.messaging.StockDeductionResult;
import ink.messaging.StockTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StockResultListener {

  private final OrderService orderService;
  private final JsonMapper jsonMapper;

  public StockResultListener(OrderService orderService, JsonMapper jsonMapper) {
    this.orderService = orderService;
    this.jsonMapper = jsonMapper;
  }

  @RabbitListener(queues = StockTopology.RESULT_QUEUE)
  public void onResult(Message message) {
    StockDeductionResult result = jsonMapper.readValue(message.getBody(), StockDeductionResult.class);
    if ("RELEASED".equals(result.status())) {
      orderService.applyReleaseResult(result.orderId());
      return;
    }
    orderService.applyStockResult(result);
  }
}
